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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PhotoOrganizer {

    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss z";
    private static final DateFormat DATE_TIME_FORMATTER = new SimpleDateFormat(DATE_TIME_FORMAT_PATTERN);

    public static void main(String[] args) throws IOException {
        Path rootPath = Paths.get(args[0]);
        Path destinationRootPath = Paths.get(args[1]);

        PhotoOrganizer organizer = new PhotoOrganizer(destinationRootPath);
        List<Path> filePaths = organizer.getAllFiles(rootPath).collect(Collectors.toList());

        System.out.printf("Found %d files%n", filePaths.size());

        Map<Classification, List<MetadataEntry>> result = filePaths
                .stream()
                .peek(p -> System.out.printf("Processing '%s'%n", p))
                .map(organizer::getMetadata)
                .peek(m -> System.out.printf("Processed '%s', classified as: %s%n", m.path, m.classification))
                .collect(Collectors.groupingBy(MetadataEntry::getClassification));

        for (Map.Entry<Classification, List<MetadataEntry>> entry : result.entrySet()) {
            // copyToDestination(path, destinationRootPath);
        }
    }

    private final Path destinationRootPath;

    public PhotoOrganizer(Path destinationRootPath) {
        this.destinationRootPath = destinationRootPath;
    }

    private Stream<Path> getAllFiles(Path rootPath) throws IOException {
        return Files.find(rootPath, 20, (p, bfa) -> bfa.isRegularFile());
    }

    private void copyToDestination(Path sourcePath) {
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

    private MetadataEntry getMetadata(Path path) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(path.toFile());
            Classification classification = classify(path, metadata);
            return new MetadataEntry(path, metadata, classification, getDateBucket);
        } catch (Exception e) {
//            System.err.printf("Exception reading metadata for file '%s': %s%n", path, e);
            return null;
        }
    }

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

    private Classification classify(Path path, Metadata entry) {
        if (path.startsWith(destinationRootPath)) {
            return Classification.SKIPPED_PATH_IN_DESTINATION;
        }

        return Classification.NEEDS_REVIEW;
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

    private enum Classification {
        SKIPPED_PATH_IN_DESTINATION,
        SKIPPED_UNKNOWN_FORMAT,
        SKIPPED_UNKNOWED_TIMESTAMP,
        SKIPPED_ALREADY_PRESENT,
        NEEDS_REVIEW,
        COPIED
    }

    private static class MetadataEntry {
        private final Path path;
        private final Metadata metadata;
        private final Classification classification;
        private final Optional<String> dateBucket;

        private MetadataEntry(
                Path path,
                Metadata metadata,
                Classification classification,
                Optional<String> dateBucket
        ) {
            this.path = path;
            this.metadata = metadata;
            this.classification = classification;
            this.dateBucket = dateBucket;
        }

        public Classification getClassification() {
            return classification;
        }
    }

}
