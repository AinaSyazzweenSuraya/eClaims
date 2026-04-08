-- ============================================================
-- E-Claims Phase 6 - Schema Additions
-- Run this in SSMS against IFC_HQ_DB_E_CLAIM
-- SAFE TO RUN: Uses IF NOT EXISTS - will not touch existing tables
-- ============================================================

USE [IFC_HQ_DB_E_CLAIM]
GO

-- ── New: User Account Table ───────────────────────────────────
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'EN_TBL_MAST_USER_ACCOUNT')
BEGIN
    CREATE TABLE [dbo].[EN_TBL_MAST_USER_ACCOUNT] (
        [UserID]      INT IDENTITY(1,1) PRIMARY KEY,
        [StaffID]     NVARCHAR(50) NOT NULL,
        [Username]    NVARCHAR(50) NOT NULL UNIQUE,
        [Password]    NVARCHAR(255) NOT NULL,
        [Role]        NVARCHAR(20) NOT NULL DEFAULT 'STAFF',
        [IsActive]    BIT NOT NULL DEFAULT 1,
        [LastLogin]   DATETIME NULL,
        [CreatedDate] DATETIME DEFAULT GETDATE(),
        CONSTRAINT FK_UserAccount_Staff FOREIGN KEY (StaffID)
            REFERENCES EN_TBL_MAST_STAFF_INFO(StaffID)
    );
    PRINT 'EN_TBL_MAST_USER_ACCOUNT created.';
END
GO

-- ── Add AttachmentPath to claim form table ────────────────────
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE object_id = OBJECT_ID('EN_TBL_DATA_CLAIM_FORM_TABLE')
    AND name = 'AttachmentPath'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_DATA_CLAIM_FORM_TABLE]
    ADD [AttachmentPath] NVARCHAR(500) NULL;
    PRINT 'AttachmentPath column added to EN_TBL_DATA_CLAIM_FORM_TABLE.';
END
GO

-- ── Add AttachmentOriginalName to claim form table ────────────
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE object_id = OBJECT_ID('EN_TBL_DATA_CLAIM_FORM_TABLE')
    AND name = 'AttachmentOriginalName'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_DATA_CLAIM_FORM_TABLE]
    ADD [AttachmentOriginalName] NVARCHAR(255) NULL;
    PRINT 'AttachmentOriginalName column added.';
END
GO

-- ── Add RowID (PK) to claim form table if not exists ─────────
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE object_id = OBJECT_ID('EN_TBL_DATA_CLAIM_FORM_TABLE')
    AND name = 'RowID'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_DATA_CLAIM_FORM_TABLE]
    ADD [RowID] INT IDENTITY(1,1) PRIMARY KEY;
    PRINT 'RowID PK added to EN_TBL_DATA_CLAIM_FORM_TABLE.';
END
GO

-- ── Add RowID (PK) to attachment table if not exists ─────────
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE object_id = OBJECT_ID('EN_TBL_DATA_CLAIM_ATTACHMENT')
    AND name = 'AttachID'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_DATA_CLAIM_ATTACHMENT]
    ADD [AttachID] INT IDENTITY(1,1) PRIMARY KEY;
    PRINT 'AttachID PK added to EN_TBL_DATA_CLAIM_ATTACHMENT.';
END
GO

-- ── Add StoredFileName to attachment table ────────────────────
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE object_id = OBJECT_ID('EN_TBL_DATA_CLAIM_ATTACHMENT')
    AND name = 'StoredFileName'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_DATA_CLAIM_ATTACHMENT]
    ADD [StoredFileName] NVARCHAR(500) NULL;
    PRINT 'StoredFileName added to EN_TBL_DATA_CLAIM_ATTACHMENT.';
END
GO

-- ── Add ContentType to attachment table ───────────────────────
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE object_id = OBJECT_ID('EN_TBL_DATA_CLAIM_ATTACHMENT')
    AND name = 'ContentType'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_DATA_CLAIM_ATTACHMENT]
    ADD [ContentType] NVARCHAR(100) NULL;
    PRINT 'ContentType added to EN_TBL_DATA_CLAIM_ATTACHMENT.';
END
GO

-- ── Add IsPrimary flag to PanelMedical (for active/inactive) ─
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE object_id = OBJECT_ID('EN_TBL_MAST_PANEL_MEDICAL')
    AND name = 'IsActive'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_MAST_PANEL_MEDICAL]
    ADD [IsActive] BIT NOT NULL DEFAULT 1;
    PRINT 'IsActive added to EN_TBL_MAST_PANEL_MEDICAL.';
END
GO

PRINT 'Schema additions complete. Existing data is untouched.';
GO

-- ── Add AuditID PK to audit log table ────────────────────────
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE object_id = OBJECT_ID('EN_TBL_DATA_AUDITLOG')
    AND name = 'AuditID'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_DATA_AUDITLOG]
    ADD [AuditID] INT IDENTITY(1,1) PRIMARY KEY;
    PRINT 'AuditID PK added to EN_TBL_DATA_AUDITLOG.';
END
GO

-- ── Add TravelID as PK if missing ────────────────────────────
-- (EN_TBL_MAST_TRAVEL had no PK originally)
IF NOT EXISTS (
    SELECT * FROM sys.key_constraints
    WHERE parent_object_id = OBJECT_ID('EN_TBL_MAST_TRAVEL')
    AND type = 'PK'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_MAST_TRAVEL]
    ALTER COLUMN [TravelID] NVARCHAR(50) NOT NULL;
    ALTER TABLE [dbo].[EN_TBL_MAST_TRAVEL]
    ADD CONSTRAINT PK_TravelID PRIMARY KEY ([TravelID]);
    PRINT 'PK added to EN_TBL_MAST_TRAVEL.';
END
GO

-- ── Add MealID as PK if missing ───────────────────────────────
IF NOT EXISTS (
    SELECT * FROM sys.key_constraints
    WHERE parent_object_id = OBJECT_ID('EN_TBL_MAST_TRAVEL_MEAL')
    AND type = 'PK'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_MAST_TRAVEL_MEAL]
    ALTER COLUMN [MealID] NVARCHAR(50) NOT NULL;
    ALTER TABLE [dbo].[EN_TBL_MAST_TRAVEL_MEAL]
    ADD CONSTRAINT PK_MealID PRIMARY KEY ([MealID]);
    PRINT 'PK added to EN_TBL_MAST_TRAVEL_MEAL.';
END
GO

-- ── Add StaffID as PK to staff claim info if missing ──────────
IF NOT EXISTS (
    SELECT * FROM sys.key_constraints
    WHERE parent_object_id = OBJECT_ID('EN_TBL_MAST_STAFF_CLAIM_INFO')
    AND type = 'PK'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_MAST_STAFF_CLAIM_INFO]
    ALTER COLUMN [StaffID] NVARCHAR(50) NOT NULL;
    ALTER TABLE [dbo].[EN_TBL_MAST_STAFF_CLAIM_INFO]
    ADD CONSTRAINT PK_StaffClaimInfo PRIMARY KEY ([StaffID]);
    PRINT 'PK added to EN_TBL_MAST_STAFF_CLAIM_INFO.';
END
GO

-- ── Add ApproverRowID PK to approver table ────────────────────
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE object_id = OBJECT_ID('EN_TBL_MAST_APPROVER')
    AND name = 'ApproverRowID'
)
BEGIN
    ALTER TABLE [dbo].[EN_TBL_MAST_APPROVER]
    ADD [ApproverRowID] INT IDENTITY(1,1) PRIMARY KEY;
    PRINT 'ApproverRowID PK added to EN_TBL_MAST_APPROVER.';
END
GO

PRINT 'All Phase 6 schema additions complete.';
GO
