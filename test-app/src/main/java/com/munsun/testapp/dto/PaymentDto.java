package com.munsun.testapp.dto;

import com.munsun.logmasking.annotation.Masked;
import com.munsun.logmasking.annotation.MaskType;
import io.swagger.v3.oas.annotations.media.Schema;

public class PaymentDto {

    @Schema(description = "Payment amount")
    private double amount;

    @Masked(type = MaskType.FINANCIAL)
    @Schema(description = "Card number")
    private String cardNumber;

    @Masked(type = MaskType.PII)
    @Schema(description = "Cardholder name")
    private String cardholderName;

    public PaymentDto() {}

    public PaymentDto(double amount, String cardNumber, String cardholderName) {
        this.amount = amount;
        this.cardNumber = cardNumber;
        this.cardholderName = cardholderName;
    }

    public double getAmount()                   { return amount; }
    public void setAmount(double v)             { this.amount = v; }
    public String getCardNumber()               { return cardNumber; }
    public void setCardNumber(String v)         { this.cardNumber = v; }
    public String getCardholderName()           { return cardholderName; }
    public void setCardholderName(String v)     { this.cardholderName = v; }

    @Override
    public String toString() {
        return "PaymentDto{amount=" + amount + ", cardNumber=" + cardNumber + ", cardholderName=" + cardholderName + '}';
    }
}
