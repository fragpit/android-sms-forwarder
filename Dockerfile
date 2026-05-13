FROM ghcr.io/cirruslabs/android-sdk:36

ARG GRADLE_VERSION=9.4.1

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip ca-certificates \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt/gradle \
    && ln -s "/opt/gradle/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle \
    && rm -f /tmp/gradle.zip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

CMD ["gradle", "--no-daemon", ":app:assembleDebug"]
