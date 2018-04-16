package com.kevinlitwack;

import static com.drew.metadata.exif.ExifDirectoryBase.TAG_DATETIME;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class App {

    public static void main(String[] args) throws IOException {
        Path rootPath = Paths.get(args[0]);
        Path destinationRootPath = Paths.get(args[1]);
        List<Path> filePaths = Files.find(rootPath, 20, (p, bfa) -> bfa.isRegularFile())
                .collect(Collectors.toList());

        System.out.printf("Found %d files%n", filePaths.size());

        for (int i = 0; i < filePaths.size(); i++) {
            Path path = filePaths.get(i);
            System.out.printf("Processing '%s': (%d/%d)%n", path, i, filePaths.size());
            copyToDestination(path, destinationRootPath);
        }
    }

    private static void copyToDestination(Path sourcePath, Path destinationRootPath) {
        if (sourcePath.startsWith(destinationRootPath)) {
            System.out.println("Skipping: file is already in destination already");
            return;
        }

        MetadataEntry entry = getMetadata(sourcePath);
        if (entry == null) {
            System.out.println("Skipping: null metadata");
            return;
        }

        Optional<Date> dateOptional = getDate(entry.metadata);
        if (!dateOptional.isPresent()) {
            System.out.println("Skipping: unable to extract date");
            return;
        }

        Date date = dateOptional.get();
        LocalDateTime ldt = LocalDateTime.ofInstant(date.toInstant(), ZoneId.of("UTC"));
        int year = ldt.getYear();
        if (year >= 2016) {
            String datePath = ldt.format(DateTimeFormatter.ofPattern("yyyy/MM"));
            Path destinationDirectory = destinationRootPath
                    .resolve(datePath);
            Path destinationPath = destinationDirectory
                    .resolve(entry.path.getFileName());
            System.out.println(buildReport(entry));
            System.out.println("Copying to: " + destinationPath);
            try {
                if (!Files.exists(destinationDirectory)) {
                    Files.createDirectory(destinationDirectory);
                }
                if (Files.exists(destinationPath)) {
                    System.out.println("Skipping: Destination file already exists");
                } else {
                    Files.copy(entry.path, destinationPath);
                }
            } catch (IOException e) {
                System.err.println("Error copying file!");
            }
        } else {
            System.out.println("Skipping: Prior to 2016");
        }
    }

    private static MetadataEntry getMetadata(Path path) {
        try {
            return new MetadataEntry(path, ImageMetadataReader.readMetadata(path.toFile()));
        } catch (Exception e) {
//            System.err.printf("Exception reading metadata for file '%s': %s%n", path, e);
            return null;
        }
    }

    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss z";
    private static final DateFormat DATE_TIME_FORMATTER = new SimpleDateFormat(DATE_TIME_FORMAT_PATTERN);

    private static String buildReport(MetadataEntry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append(entry.path).append(": ");

        Optional<Date> date = getDate(entry.metadata);
        if (date.isPresent()) {
            String dateStr = DATE_TIME_FORMATTER.format(date.get());
            builder.append("ExifIFD0 Date/Time: ").append(dateStr);
        } else {
            builder.append("NOT FOUND");
        }

        return builder.toString();
    }

    private static Optional<Date> getDate(Metadata metadata) {
        Collection<ExifIFD0Directory> exifIFD0Directories =
                metadata.getDirectoriesOfType(ExifIFD0Directory.class);

        if (exifIFD0Directories.isEmpty()) {
            return Optional.empty();
        } else {
            List<Date> dates = exifIFD0Directories.stream()
                    .map(d -> Optional.ofNullable(d.getDate(TAG_DATETIME)))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            if (dates.isEmpty()) {
                return Optional.empty();
            } else if (dates.size() > 1) {
                throw new IllegalStateException("Found multiple dates");
            } else {
                return Optional.of(dates.get(0));
            }
        }
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
