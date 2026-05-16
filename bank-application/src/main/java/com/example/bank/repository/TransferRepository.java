package com.example.bank.repository;

import com.example.bank.domain.Transfer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(UUID id);

    List<Transfer> findAll();
}
