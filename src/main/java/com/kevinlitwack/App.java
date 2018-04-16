package com.kevinlitwack;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class App {

    public static void main(String[] args) throws IOException {
        String rootPath = args[0];
        List<Path> filePaths = Files.find(Paths.get(rootPath), 20, (p, bfa) -> bfa.isRegularFile())
                .collect(Collectors.toList());

        System.out.printf("Found %d files%n", filePaths.size());

        filePaths.stream()
                .map(App::getMetadata)
                .filter(Objects::nonNull)
                .map(App::buildReport)
                .forEach(System.out::println);
    }

    private static MetadataEntry getMetadata(Path path) {
        try {
            return new MetadataEntry(path, ImageMetadataReader.readMetadata(path.toFile()));
        } catch (ImageProcessingException | IOException e) {
            System.err.printf("Exception reading metadata for file '%s': %s%n", path, e);
            return null;
        }
    }

    private static String buildReport(MetadataEntry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append(entry.path).append(System.lineSeparator());
        entry.metadata.getDirectories().forEach(d -> {
            for (Tag tag : d.getTags()) {
                builder.append(tag).append(System.lineSeparator());
            }

            for (String error : d.getErrors()) {
                builder.append("ERROR: ").append(error).append(System.lineSeparator());
            }
        });
        return builder.toString();
    }

    private static class MetadataEntry {
        private final Path path;
        private final Metadata metadata;

        private MetadataEntry(Path path, Metadata metadata) {
            this.path = path;
            this.metadata = metadata;
        }
    }

}
