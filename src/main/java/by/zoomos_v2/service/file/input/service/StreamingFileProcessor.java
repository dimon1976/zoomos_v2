package by.zoomos_v2.service.file.input.service;

import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.service.file.input.processor.ProcessingProgressCallback;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface StreamingFileProcessor {

    void processFileStreaming(Path filePath, FileMetadata metadata,
                              Path tempOutputPath,
                              ProcessingProgressCallback progressCallback,
                              List<String> headers) throws IOException;
    List<String> readHeaders(Path filePath, FileMetadata metadata) throws IOException;
    long countLines(Path filePath, FileMetadata metadata) throws IOException;
}
