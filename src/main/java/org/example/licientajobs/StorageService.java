package org.example.licientajobs;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.io.ByteArrayInputStream;

@Service
public class StorageService {

    private final Path rootLocation = Paths.get("student-uploads");

    public StorageService() {
        try {
            Files.createDirectories(rootLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create upload directory!", ex);
        }
    }

    public String store(MultipartFile file, Long studentId) {
        try {
            return store(file.getBytes(), studentId, file.getOriginalFilename());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file.", e);
        }
    }

    public String store(byte[] fileBytes, Long studentId, String originalFilename) {
        try {
            if (fileBytes == null || fileBytes.length == 0) {
                throw new RuntimeException("Failed to store empty file.");
            }
            Path studentDirectory = rootLocation.resolve(String.valueOf(studentId));
            Files.createDirectories(studentDirectory);

            Path destinationFile = studentDirectory.resolve(
                    Paths.get(originalFilename)).normalize().toAbsolutePath();
            
            if (!destinationFile.getParent().equals(studentDirectory.toAbsolutePath())) {
                throw new RuntimeException(
                        "Cannot store file outside current directory.");
            }
            
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes)) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return originalFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file.", e);
        }
    }

    public Path load(Long studentId, String filename) {
        return rootLocation.resolve(String.valueOf(studentId)).resolve(filename);
    }
}