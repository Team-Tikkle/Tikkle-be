package com.tikkle.investment.repository;

import com.tikkle.investment.entity.Coin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoinRepository extends JpaRepository<Coin, String> {
    List<Coin> findAllByIsActiveTrue();
}
