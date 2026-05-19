# Use a base image with Java
FROM ibm-semeru-runtimes:open-17-jre-focal
ENV PORT 8080
EXPOSE 8080
# Copy the built jar file into the image
COPY target/project-phoenix-1.0.4-SNAPSHOT.jar app.jar

# Set the entry point to run your application
CMD java -jar app.jar --server.address=0.0.0.0