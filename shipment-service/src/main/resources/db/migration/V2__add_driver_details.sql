-- Copied from auth-service when a driver takes the job, so customers can see who is bringing their parcel
-- without being allowed to look users up.
ALTER TABLE shipments
    ADD COLUMN driver_name            VARCHAR(100) NULL,
    ADD COLUMN driver_phone           VARCHAR(20)  NULL,
    ADD COLUMN expected_delivery_date DATE         NULL;
