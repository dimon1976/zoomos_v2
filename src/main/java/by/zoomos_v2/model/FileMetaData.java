package by.zoomos_v2.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
public class FileMetaData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileExtension;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, unique = true)
    private String fileHash;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    public FileMetaData() {

    }

    public FileMetaData(String fileName, String s, String s1, LocalDateTime now, Long fileSize, String fileHash) {
    }
}
