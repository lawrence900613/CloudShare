package com.example.fileshare.file;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {
    /**
     * Returns a page of files for one owner ordered from newest to oldest.
     * Used by the list endpoint to support pagination.
     */
    List<FileObject> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    /**
     * Looks up a file by id scoped to an owner for access control.
     * Empty result means the file does not belong to that owner or does not exist.
     */
    Optional<FileObject> findByIdAndOwnerId(Long id, Long ownerId);

    /**
     * Finds a file by owner and exact S3 key for delete/sync operations.
     * This keeps storage actions tied to the correct account.
     */
    Optional<FileObject> findByOwnerIdAndS3Key(Long ownerId, String s3Key);

    /**
     * Counts how many files are stored for one owner.
     * Used to enforce the per-user upload limit.
     */
    long countByOwnerId(Long ownerId);
}
