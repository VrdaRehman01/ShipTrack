-- Optional: mainly for drivers, so customers can call the person bringing their parcel.
ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL;
