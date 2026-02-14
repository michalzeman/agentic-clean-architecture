--liquibase formatted sql
--changeset migration-team:1
--comment: Create bank-account schema

CREATE SCHEMA IF NOT EXISTS "bank-account";
