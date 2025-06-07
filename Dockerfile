# Use official OpenJDK runtime as a base image
FROM eclipse-temurin:21-jdk-jammy

# Set working directory inside container
WORKDIR /app

# Copy Maven wrapper and build files first for caching dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (so they cache if code doesn't change)
RUN ./mvnw dependency:go-offline

# Copy the rest of the application code
COPY src ./src

# Package the application
RUN ./mvnw package -DskipTests

# Install ffmpeg and ffprobe for yt-dlp postprocessing
RUN apt-get update && apt-get install -y ffmpeg

# Install yt-dlp
RUN apt-get install -y python3 python3-pip && \
    pip3 install yt-dlp

# Create necessary directories
RUN mkdir -p /app/logs && chmod 777 /app/logs
RUN mkdir -p /app/downloads && chmod 777 /app/downloads

# Set environment variables with default values
ENV APP_PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV TELEGRAM_BOT_TOKEN=your_telegram_bot_token_here
ENV PUBLIC_DOWNLOAD_PATH=downloads
# PUBLIC_DOWNLOAD_URL must be provided at runtime based on actual deployment URL
ENV PUBLIC_DOWNLOAD_URL=REQUIRED_AT_RUNTIME
ENV TELEGRAM_BOT_FILE_MAX_SIZE=50
ENV PUBLIC_DOWNLOAD_FILE_MAX_SIZE=500
ENV YT_DLP_FFMPEG_PATH=/usr/bin/ffmpeg

# Expose the port (Railway will detect this)
EXPOSE ${APP_PORT}

# Run the application with the configurable port and environment variables
CMD ["sh", "-c", "if [ \"$PUBLIC_DOWNLOAD_URL\" = \"REQUIRED_AT_RUNTIME\" ]; then echo \"ERROR: PUBLIC_DOWNLOAD_URL environment variable must be set\"; exit 1; fi && \
    java -jar target/youtube-telegram-downloader.jar \
    --server.port=${PORT:-$APP_PORT} \
    --telegram.bot.token=${TELEGRAM_BOT_TOKEN} \
    --public.download.path=${PUBLIC_DOWNLOAD_PATH} \
    --public.download.url=${PUBLIC_DOWNLOAD_URL} \
    --telegram.bot.file.max-size=${TELEGRAM_BOT_FILE_MAX_SIZE} \
    --public.download.file.max-size=${PUBLIC_DOWNLOAD_FILE_MAX_SIZE} \
    --yt-dlp.ffmpeg.path=${YT_DLP_FFMPEG_PATH}"]
