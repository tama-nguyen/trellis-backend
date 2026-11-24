package io.trellis.learningcontent.infrastructure.persistence.entity;

import io.trellis.learningcontent.api.dto.CourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Row of {@code course}.
 *
 * <p>{@code version} is a plain column, not {@code @Version}: the aggregate decides when it
 * advances and the update statement checks it, so Hibernate must not also own that number.
 */
@Entity
@Table(name = "course")
@Getter
@Setter
@NoArgsConstructor
public class CourseEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "code", nullable = false, length = 17)
  private String code;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "description", nullable = false, columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private CourseStatus status;

  @Column(name = "owner_id", nullable = false, length = 200)
  private String ownerId;

  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;
}
