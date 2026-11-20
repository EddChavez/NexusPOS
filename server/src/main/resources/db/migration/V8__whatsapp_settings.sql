-- V8: Configuración para Envío Digital de Tickets y Saldos por WhatsApp

INSERT INTO settings (setting_key, setting_value) VALUES ('whatsapp_country_code', '52') ON CONFLICT DO NOTHING;
INSERT INTO settings (setting_key, setting_value) VALUES ('whatsapp_bank_details', '') ON CONFLICT DO NOTHING;
INSERT INTO settings (setting_key, setting_value) VALUES ('whatsapp_custom_footer', '¡Gracias por su preferencia!') ON CONFLICT DO NOTHING;
