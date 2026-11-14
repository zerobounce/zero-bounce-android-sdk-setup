# ZeroBounce Android SDK – unit tests (JVM only, no emulator)
FROM eclipse-temurin:17-jammy

RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip \
    wget \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_SDK_ROOT=/opt/android-sdk
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools \
    && mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

ENV PATH="${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin"
RUN yes | sdkmanager --licenses 2>/dev/null || true \
    && sdkmanager "platform-tools" "platforms;android-36" "platforms;android-37.0" "build-tools;37.0.0"

WORKDIR /app

# Owned by the unprivileged user the tests run as, so Gradle can write build output into /app.
COPY --chown=65532:65532 . .

# Run unit tests for the SDK library only (no emulator)

# The unprivileged user has no home directory: give Gradle (and the tools that read $HOME) a writable one.
RUN mkdir -p /home/gradle && chown 65532:65532 /home/gradle /app
ENV HOME=/home/gradle GRADLE_USER_HOME=/home/gradle/.gradle

USER 65532:65532

CMD ["./gradlew", ":zero_bounce_sdk:test", "--no-daemon", "-q"]
