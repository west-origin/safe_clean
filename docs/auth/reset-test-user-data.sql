-- User approved resetting only safe_clean_test on 2026-11-09.
-- Configuration, catalog, terms, migrations and inactive production are excluded.
begin;
delete from public.payment_refunds;
delete from public.cleaning_photos;
delete from public.recording_segments;
delete from public.addresses;
delete from public.booking_status_history;
delete from public.customer_housing;
delete from public.idempotency_keys;
delete from public.inquiries;
delete from public.job_checklist_items;
delete from public.job_pauses;
delete from public.livekit_events;
delete from public.manager_devices;
delete from public.manager_hours;
delete from public.manager_penalties;
delete from public.manager_profiles;
delete from public.manager_unavailable;
delete from public.notification_settings;
delete from public.offers;
delete from public.outbox_events;
delete from public.phone_verifications;
delete from public.push_tokens;
delete from public.repair_photo_uploads;
delete from public.revoked_sessions;
delete from public.settlements;
delete from public.term_agreements;
delete from public.job_checklist_events;
delete from public.live_sessions;
delete from public.payments;
delete from public.bookings;
delete from public.payment_methods;
delete from public.booking_series;
delete from public.profiles;
delete from auth.users;
commit;

