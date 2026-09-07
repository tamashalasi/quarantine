FROM eclipse-temurin:17.0.18_8-jdk-jammy@sha256:978ed38b7785312f7761bee5e24cfcd7ac2fe466c5f07acee7b322e0bae6d0ec AS java
FROM python:3.12.9-slim-bookworm@sha256:48a11b7ba705fd53bf15248d1f94d36c39549903c5d59edcfa2f3f84126e7b44
COPY --from=java /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk ANDROID_HOME=/opt/android-sdk \
    PATH=/opt/java/openjdk/bin:/opt/android-sdk/build-tools/34.0.0:/usr/local/bin:/usr/bin:/bin \
    LANG=C.UTF-8 LC_ALL=C.UTF-8 TZ=UTC GRADLE_USER_HOME=/gradle-cache
COPY build-support/install-sdk.py /opt/install-sdk.py
COPY build-support/requirements.txt /opt/requirements.txt
RUN python /opt/install-sdk.py && pip install --no-cache-dir --require-hashes -r /opt/requirements.txt
WORKDIR /workspace
ENTRYPOINT ["python", "/src/build-support/build.py"]
