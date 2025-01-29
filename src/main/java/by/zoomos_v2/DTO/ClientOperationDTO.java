package by.zoomos_v2.DTO;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * DTO для передачи информации об операции клиента
 */
@Data
public class ClientOperationDTO {
    private Long id;
    private String type;
    private String status;
    private LocalDateTime timestamp;
}
