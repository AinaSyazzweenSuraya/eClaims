FROM eclipse-temurin:17-jre

# Fonts: needed by Apache POI (Excel auto-size columns) and PDF generation
RUN apt-get update && apt-get install -y --no-install-recommends \
      fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/eclaims-*.jar app.jar

RUN useradd -r -u 1001 spring && mkdir -p /app/uploads && chown spring /app/uploads
USER spring

ENV JAVA_OPTS="-Xmx512m"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]