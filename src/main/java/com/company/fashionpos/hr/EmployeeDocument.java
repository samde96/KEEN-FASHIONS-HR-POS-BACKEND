package com.company.fashionpos.hr;

import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.staff.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_documents")
public class EmployeeDocument {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "uploaded_by_user_id")
  private UserAccount uploadedByUser;

  @Column(name = "document_type", nullable = false, length = 80)
  private String documentType;

  @Column(nullable = false, length = 160)
  private String title;

  @Column(name = "file_name", nullable = false, length = 220)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 120)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(length = 1000)
  private String notes;

  @Column(name = "file_data", nullable = false)
  private byte[] fileData;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected EmployeeDocument() {}

  public EmployeeDocument(
      UUID id,
      Organization organization,
      Employee employee,
      UserAccount uploadedByUser,
      String documentType,
      String title,
      String fileName,
      String contentType,
      long sizeBytes,
      String notes,
      byte[] fileData) {
    this.id = id;
    this.organization = organization;
    this.employee = employee;
    this.uploadedByUser = uploadedByUser;
    this.documentType = documentType;
    this.title = title;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.notes = notes;
    this.fileData = fileData;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    uploadedAt = uploadedAt == null ? now : uploadedAt;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public Employee getEmployee() {
    return employee;
  }

  public UserAccount getUploadedByUser() {
    return uploadedByUser;
  }

  public String getDocumentType() {
    return documentType;
  }

  public String getTitle() {
    return title;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getNotes() {
    return notes;
  }

  public byte[] getFileData() {
    return fileData;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }
}
