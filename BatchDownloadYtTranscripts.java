///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS com.google.code.gson:gson:2.11.0

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

void main(String... args) {
    if (args.length == 0) {
        System.out.println("Usage: BatchDownloadYtTranscripts <channel-url> [--limit N]");
        System.out.println("Example: BatchDownloadYtTranscripts https://www.youtube.com/@JitteredTV");
        System.out.println("Example: BatchDownloadYtTranscripts https://www.youtube.com/@JitteredTV --limit 1");
        System.exit(1);
    }

    String channelUrl = args[0];
    Integer limit = null;

    // Parse optional --limit argument
    for (int i = 1; i < args.length; i++) {
        if (args[i].equals("--limit") && i + 1 < args.length) {
            try {
                limit = Integer.parseInt(args[i + 1]);
            } catch (NumberFormatException e) {
                System.err.println("❌ Invalid limit value: " + args[i + 1]);
                System.exit(1);
            }
        }
    }

    System.out.println("📺 Processing channel: " + channelUrl);
    if (limit != null) {
        System.out.println("⚠️  Limit: Processing only " + limit + " video(s)");
    }

    try {
        // Get channel info and video URLs
        ChannelInfo channelInfo = getChannelInfo(channelUrl);
        System.out.println("📁 Channel: " + channelInfo.channelName());
        System.out.println("🎬 Found " + channelInfo.videoUrls().size() + " videos");

        // Apply limit if specified
        List<String> videosToProcess = channelInfo.videoUrls();
        if (limit != null && limit < videosToProcess.size()) {
            videosToProcess = videosToProcess.subList(0, limit);
            System.out.println("🎯 Processing first " + limit + " video(s)");
        }

        // Create output directory
        Path outputDir = Paths.get(sanitizeFileName(channelInfo.channelName()));
        Files.createDirectories(outputDir);
        System.out.println("📂 Output directory: " + outputDir.toAbsolutePath());

        // Download transcripts concurrently
        downloadTranscripts(videosToProcess, outputDir);

        System.out.println("✅ All transcripts downloaded successfully!");

    } catch (Exception e) {
        System.err.println("❌ Error: " + e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }
}

record ChannelInfo(String channelName, List<String> videoUrls) {}

record VideoInfo(String url, String title) {}

/**
 * Gets channel information including name and all video URLs
 */
ChannelInfo getChannelInfo(String channelUrl) throws IOException, InterruptedException {
    System.out.println("🔍 Fetching channel information...");

    // Get channel metadata and video list using yt-dlp
    ProcessBuilder pb = new ProcessBuilder(
        "yt-dlp",
        "--dump-json",
        "--flat-playlist",
        "--skip-download",
        channelUrl
    );

    Process process = pb.start();

    List<String> videoUrls = new ArrayList<>();
    String channelName = null;

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();

            // Get channel name from first entry
            if (channelName == null && json.has("channel")) {
                channelName = json.get("channel").getAsString();
            }

            // Get video URL
            if (json.has("id")) {
                String videoId = json.get("id").getAsString();
                videoUrls.add("https://www.youtube.com/watch?v=" + videoId);
            }
        }
    }

    int exitCode = process.waitFor();
    if (exitCode != 0) {
        throw new IOException("yt-dlp failed with exit code " + exitCode);
    }

    // Fallback channel name if not found
    if (channelName == null) {
        channelName = "youtube_channel_" + System.currentTimeMillis();
    }

    return new ChannelInfo(channelName, videoUrls);
}

/**
 * Downloads transcripts for all videos concurrently
 */
void downloadTranscripts(List<String> videoUrls, Path outputDir) throws InterruptedException {
    int threadCount = Math.min(4, Runtime.getRuntime().availableProcessors()); // Limit to 4 concurrent downloads
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    System.out.println("⚡ Downloading transcripts with " + threadCount + " threads...");

    for (int i = 0; i < videoUrls.size(); i++) {
        String videoUrl = videoUrls.get(i);
        int videoNumber = i + 1;
        int totalVideos = videoUrls.size();

        executor.submit(() -> {
            try {
                downloadAndConvertTranscript(videoUrl, outputDir, videoNumber, totalVideos);
            } catch (Exception e) {
                System.err.println("❌ Failed to download transcript for " + videoUrl + ": " + e.getMessage());
            }
        });
    }

    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.HOURS);
}

/**
 * Downloads a single video transcript and converts to markdown
 */
void downloadAndConvertTranscript(String videoUrl, Path outputDir, int videoNumber, int totalVideos)
        throws IOException, InterruptedException {

    // Create temp directory for VTT file
    Path tempDir = outputDir.resolve(".temp");
    Files.createDirectories(tempDir);

    System.out.println("📥 [" + videoNumber + "/" + totalVideos + "] Downloading: " + videoUrl);

    // Download VTT file
    ProcessBuilder pb = new ProcessBuilder(
        "yt-dlp",
        "--write-auto-subs",
        "--sub-format", "vtt",
        "--skip-download",
        "-o", tempDir.resolve("%(title)s").toString(),
        videoUrl
    );

    Process process = pb.start();
    int exitCode = process.waitFor();

    if (exitCode != 0) {
        throw new IOException("yt-dlp failed with exit code " + exitCode);
    }

    // Find the downloaded VTT file
    List<Path> vttFiles = Files.list(tempDir)
        .filter(p -> p.toString().endsWith(".vtt"))
        .collect(Collectors.toList());

    if (vttFiles.isEmpty()) {
        System.out.println("⚠️  [" + videoNumber + "/" + totalVideos + "] No transcript available for: " + videoUrl);
        return;
    }

    // Convert VTT to Markdown
    for (Path vttFile : vttFiles) {
        String markdownContent = convertVttToMarkdown(vttFile, videoUrl);

        // Create markdown filename
        String fileName = vttFile.getFileName().toString();
        fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        // Remove language suffix like ".en"
        fileName = fileName.replaceAll("\\.\\w{2}$", "");
        fileName = sanitizeFileName(fileName) + ".md";

        Path markdownFile = outputDir.resolve(fileName);
        Files.writeString(markdownFile, markdownContent);

        System.out.println("✅ [" + videoNumber + "/" + totalVideos + "] Saved: " + fileName);

        // Delete VTT file
        Files.delete(vttFile);
    }
}

/**
 * Converts VTT file to clean Markdown format
 */
String convertVttToMarkdown(Path vttFile, String videoUrl) throws IOException {
    List<String> lines = Files.readAllLines(vttFile);
    StringBuilder markdown = new StringBuilder();

    // Extract video title from filename
    String fileName = vttFile.getFileName().toString();
    String title = fileName.substring(0, fileName.lastIndexOf('.'));
    title = title.replaceAll("\\.\\w{2}$", ""); // Remove language suffix

    // Add header
    markdown.append("# ").append(title).append("\n\n");
    markdown.append("**Video URL:** ").append(videoUrl).append("\n\n");
    markdown.append("---\n\n");
    markdown.append("## Transcript\n\n");

    // Parse VTT content
    Pattern timestampPattern = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} --> \\d{2}:\\d{2}:\\d{2}\\.\\d{3}");
    Pattern wordTimingPattern = Pattern.compile("<\\d{2}:\\d{2}:\\d{2}\\.\\d{3}><c>");

    StringBuilder currentParagraph = new StringBuilder();
    String previousLine = "";

    for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i).trim();

        // Skip empty lines, WEBVTT header, and metadata
        if (line.isEmpty() || line.startsWith("WEBVTT") || line.startsWith("Kind:") || line.startsWith("Language:")) {
            continue;
        }

        // Skip timestamp lines
        if (timestampPattern.matcher(line).find()) {
            continue;
        }

        // Skip lines with word-level timing tags
        if (wordTimingPattern.matcher(line).find()) {
            continue;
        }

        // Skip common noise markers
        if (line.equals("[Music]") || line.equals("[Applause]") || line.matches("\\[.*\\]")) {
            continue;
        }

        // Skip duplicate lines (VTT format repeats captions across timestamp blocks)
        if (line.equals(previousLine)) {
            continue;
        }

        // Add text to paragraph
        if (!line.isEmpty()) {
            if (currentParagraph.length() > 0) {
                currentParagraph.append(" ");
            }
            currentParagraph.append(line);
            previousLine = line;

            // Create paragraph breaks every ~500 characters for readability
            if (currentParagraph.length() > 500) {
                markdown.append(currentParagraph.toString().trim()).append("\n\n");
                currentParagraph = new StringBuilder();
            }
        }
    }

    // Add any remaining text
    if (currentParagraph.length() > 0) {
        markdown.append(currentParagraph.toString().trim()).append("\n");
    }

    return markdown.toString();
}

/**
 * Sanitizes a filename by removing invalid characters
 */
String sanitizeFileName(String name) {
    // Replace invalid filename characters
    return name.replaceAll("[：:？?＂\"<>|*\\\\]", "")
               .replaceAll("\\s+", "_")
               .replaceAll("_+", "_")
               .replaceAll("^_|_$", "")
               .trim();
}
