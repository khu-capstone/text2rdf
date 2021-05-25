FROM openjdk:11
COPY out/artifacts/text2rdf_jar/ /usr/src/text2rdf
WORKDIR /usr/src/text2rdf
EXPOSE 8001
ENTRYPOINT java -jar text2rdf.jar
