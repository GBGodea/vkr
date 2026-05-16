package com.example.bank.repository;

import com.example.bank.domain.Transfer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTransferRepository implements TransferRepository {

    private final ConcurrentMap<UUID, Transfer> transfers = new ConcurrentHashMap<>();

    @Override
    public Transfer save(Transfer transfer) {
        transfers.put(transfer.getId(), transfer);
        return transfer;
    }

    @Override
    public Optional<Transfer> findById(UUID id) {
        return Optional.ofNullable(transfers.get(id));
    }

    @Override
    public List<Transfer> findAll() {
        return transfers.values().stream()
                .sorted(Comparator.comparing(Transfer::getCreatedAt))
                .toList();
    }
}
