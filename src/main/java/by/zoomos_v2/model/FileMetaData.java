package by.zoomos_v2.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
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
}
