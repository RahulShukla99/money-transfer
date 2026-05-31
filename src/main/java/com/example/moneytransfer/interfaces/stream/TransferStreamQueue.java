package com.example.moneytransfer.interfaces.stream;

public interface TransferStreamQueue {

    void enqueue(TransferStreamWorkItem workItem);

    TransferStreamWorkItem take() throws InterruptedException;

    int size();
}
