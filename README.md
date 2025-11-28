# YouTube Channel Transcript Downloader

A JBang script that downloads all video transcripts from a YouTube channel and converts them to well-formatted Markdown files.

## Prerequisites

1. **JBang**: Install from [jbang.dev](https://www.jbang.dev/)
2. **yt-dlp**: Install using `pip install yt-dlp` or from [yt-dlp GitHub](https://github.com/yt-dlp/yt-dlp)
3. **Java 25+**: The script requires Java 25 or later

## Installation

```bash
# Install JBang (macOS)
brew install jbang

# Install JBang (Linux)
curl -Ls https://sh.jbang.dev | bash -s - app setup

# Install yt-dlp
pip install yt-dlp
# or
brew install yt-dlp
```

## Usage

### Download all transcripts from a channel

```bash
./BatchDownloadYtTranscripts.java "https://www.youtube.com/@ChannelName"
```

Or using JBang explicitly:

```bash
jbang BatchDownloadYtTranscripts.java "https://www.youtube.com/@ChannelName"
```

### Test with a single video (recommended for first run)

```bash
./BatchDownloadYtTranscripts.java "https://www.youtube.com/@ChannelName" --limit 1
```

This will download the **most recent video** from the channel.

### Process a specific number of videos

```bash
./BatchDownloadYtTranscripts.java "https://www.youtube.com/@ChannelName" --limit 5
```

This will download the **5 most recent videos** from the channel.

**Note:** Videos are automatically sorted by upload date (newest first), so using `--limit` always gives you the most recent videos.

The script will:
1. Fetch all video URLs from the specified channel
2. Download VTT subtitles for each video
3. Convert VTT files to clean Markdown format
4. Save files in a directory named after the channel
5. Download transcripts concurrently using virtual threads

### Output Structure

```
ChannelName/
├── Video_Title_1.md
├── Video_Title_2.md
└── Video_Title_3.md
```

Each Markdown file contains:
- Video title as header
- Link to original video
- Clean transcript text with paragraph breaks

## Features

- **Virtual Thread Concurrency**: Uses Java virtual threads for efficient concurrent downloads
- **Auto-Generated Channel Directories**: Organizes files by channel name
- **Clean Markdown Formatting**:
  - Removes VTT timing information
  - Filters duplicate text
  - Removes noise markers like [Music] and [Applause]
  - Continuous transcript optimized for LLM consumption
- **Filename Sanitization**: Handles special characters in video titles
- **Progress Tracking**: Shows download progress for each video
- **Error Handling**: Continues processing even if some videos don't have transcripts

## How It Works

1. **Channel Discovery**: Uses `yt-dlp --flat-playlist` to get all video IDs from the channel
2. **Transcript Download**: For each video, downloads auto-generated subtitles in VTT format
3. **VTT Parsing**: Extracts clean text by:
   - Filtering timestamp lines
   - Removing word-level timing tags
   - Detecting and removing duplicate lines
   - Skipping noise markers
4. **Markdown Generation**: Creates formatted markdown with headers and metadata
5. **Cleanup**: Removes temporary VTT files after conversion

## Use Cases

- **LLM Training & Analysis**: Continuous transcript format optimized for LLM consumption
- Research and analysis of content creators' work
- Creating searchable archives of educational content
- Analyzing speaking patterns and technical terminology
- Blog post research (like analyzing Ted M. Young's development workflow)
- Accessibility and content indexing

## Limitations

- Only works with videos that have auto-generated or manual subtitles
- Transcript quality depends on YouTube's speech recognition
- Some videos may not have transcripts available
- Requires Java 25+ for virtual thread support

## Testing

A test script is included to verify VTT to Markdown conversion:

```bash
java --enable-preview --source 21 TestVttConversion.java
```

This will convert the example VTT file and output to `test_output.md`.

## Technical Details

- **Language**: Java 25+ (using modern features like implicit main methods and virtual threads)
- **Build Tool**: JBang (zero-setup Java scripting)
- **Dependencies**: Gson 2.13.2 (for JSON parsing of yt-dlp output)
- **External Tools**: yt-dlp (for YouTube metadata and subtitle download)
- **Concurrency**: Virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`

## Why Not JobRunr?

JobRunr is a distributed job scheduling library designed for:
- Background job processing with persistence
- Distributed task execution across multiple servers
- Job monitoring and retry mechanisms
- Complex scheduling patterns

For this use case, JobRunr would be overkill because:
- Simple batch processing task
- No need for persistence or retry logic
- No distributed execution requirements
- Java's built-in `ExecutorService` provides sufficient concurrent execution

## Troubleshooting

**No transcripts found**:
- Some videos don't have auto-generated subtitles
- Private or restricted videos can't be accessed
- Try with a different channel

**Permission denied when running script**:
```bash
chmod +x BatchDownloadYtTranscripts.java
```

**yt-dlp errors**:
- Update yt-dlp: `pip install --upgrade yt-dlp`
- Check video accessibility in browser

**Unicode/encoding issues**:
- The script handles special characters in filenames
- Output is UTF-8 encoded Markdown

## License

Free to use for research and educational purposes.

## Contributing

Feel free to submit issues or improvements!
