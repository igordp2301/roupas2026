package com.unilopers.roupas.async;

import com.unilopers.roupas.domain.InstallmentPayment;
import com.unilopers.roupas.domain.Orders;
import com.unilopers.roupas.repository.InstallmentPaymentRepository;
import com.unilopers.roupas.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstallmentPaymentAsyncService {

    private static final int DEFAULT_INSTALLMENTS = 3;

    private final OrderRepository orderRepository;
    private final InstallmentPaymentRepository installmentPaymentRepository;

    @Async("installmentTaskExecutor")
    @Transactional
    public void generateInstallmentsAsync(UUID orderId) {
        try {
            log.info("Starting async installment generation. orderId={}", orderId);

            Orders order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                log.warn("Order not found during async installment generation. orderId={}", orderId);
                return;
            }

            double totalAmount = sanitizeAmount(order.getTotalAmount());
            if (totalAmount <= 0.0) {
                log.warn("Order has invalid totalAmount. orderId={}, totalAmount={}", orderId, totalAmount);
                return;
            }

            double installmentAmount = roundMoney(totalAmount / DEFAULT_INSTALLMENTS);

            for (int i = 1; i <= DEFAULT_INSTALLMENTS; i++) {
                InstallmentPayment payment = new InstallmentPayment();
                payment.setId(UUID.randomUUID().toString());
                payment.setCreatedAt(LocalDateTime.now());
                payment.setOrderId(order.getOrderId().toString());
                payment.setInstallmentNumber(i);
                payment.setAmount(installmentAmount);
                payment.setMaturity(LocalDate.now().plusMonths(i));
                payment.setPaid(false);
                payment.setPaymentDate(null);
                payment.setMethod(null);

                installmentPaymentRepository.save(payment);
            }

            log.info(
                    "Async installment generation finished. orderId={}, installments={}, installmentAmount={}",
                    orderId, DEFAULT_INSTALLMENTS, installmentAmount
            );
        } catch (Exception ex) {
            log.error("Error while generating installments asynchronously. orderId={}", orderId, ex);
        }
    }

    private double sanitizeAmount(Double rawAmount) {
        double amount = rawAmount == null ? 0.0 : rawAmount;
        return Math.max(amount, 0.0);
    }

    private double roundMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}