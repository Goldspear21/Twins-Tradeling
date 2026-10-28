-- Tradeling premade records for the local MySQL trading database.
-- Loaded after Hibernate creates/updates tables. Only missing keys are inserted;
-- existing catalog details, stock, ledger history and saved plans are left unchanged.
-- All date offsets use the fixed 2026-10-27 reference date, not the startup time.
-- Existing reference identifiers are retained to avoid duplicating imported records.


-- -------------------------------------------------------------------------------------
-- 1. CATALOG - 25 SKUs, every one with a balance in all three locations
--    abu_dhabi_stock / dubai_stock / sharjah_stock are the CURRENT balances.
-- -------------------------------------------------------------------------------------
INSERT INTO products (sku, name, family, description, brand, unit_price, cost_price,
                      abu_dhabi_stock, dubai_stock, sharjah_stock,
                      reorder_point, supplier_lead_time_days, created_at, version)
VALUES
  ('MOU-WIRED-001', 'Essential wired mouse',            'MOUSE',    'USB-A, optical 1200 DPI, three buttons, 1.2 m cable',                        'Nexa',    29.00,  15.50,   0,   0,   0, 20,  5, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('MOU-BT-002',    'Bluetooth travel mouse',           'MOUSE',    'Bluetooth 5.2, silent clicks, USB-C rechargeable, 68 g body',                 'Nexa',    79.00,  42.00,   8,  64,  30, 25,  7, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('MOU-24G-003',   'Wireless desktop mouse',           'MOUSE',    '2.4 GHz USB receiver, ergonomic grip, 1600 DPI optical sensor',               'Nexa',    59.00,  31.00,  96, 120,   0, 20,  6, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('MOU-GAME-004',  'RGB gaming mouse',                 'MOUSE',    'Wired USB, 7200 DPI, six programmable buttons, 26000 DPI sensor',             'Voltra', 119.00,  63.00,  34,  40,   5, 30, 10, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('MOU-ERG-005',   'Ergonomic vertical mouse',         'MOUSE',    '57-degree vertical grip, 4000 DPI, USB-C rechargeable',                       'Kestrel', 99.00,  52.00,  15,  15,  15, 15,  9, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('KEY-WIRED-001', 'Full-size wired keyboard',         'KEYBOARD', 'USB-A, 104-key layout, spill-resistant membrane',                            'Nexa',    49.00,  26.00,  55,  78,  42, 20,  5, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('KEY-BT-002',    'Compact Bluetooth keyboard',       'KEYBOARD', 'Multi-device Bluetooth, compact 78-key layout, Mac/Windows layout',          'Nexa',   129.00,  69.00,  25,  68,  22, 25,  8, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('KEY-MECH-003',  'Mechanical gaming keyboard',       'KEYBOARD', 'USB-C, tactile switches, RGB backlight, hot-swappable keys',                  'Voltra', 219.00, 118.00,  12,  90,  55, 30, 12, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('KEY-COMBO-004', 'Wireless keyboard and mouse kit',  'KEYBOARD', 'Full-size keyboard, mouse and shared 2.4 GHz receiver',                         'Nexa',   149.00,  80.00,  40,  52,  60, 20,  9, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('KEY-LAPTOP-005','Slim laptop keyboard',             'KEYBOARD', 'Low-profile scissor switches, Bluetooth, 13-inch layout',                      'Kestrel', 69.00,  37.00,   0,  33,  28, 15,  6, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('CHG-20W-001',   '20 W USB-C wall charger',          'CHARGER',  'USB Power Delivery, single USB-C port, UAE plug',                           'Voltra',  45.00,  23.00,  88, 140,  65, 30,  6, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('CHG-30W-002',   '30 W compact GaN charger',         'CHARGER',  'GaN, USB-C PD, foldable UAE plug, 1 m detachable cable',                      'Voltra',  75.00,  40.00,  24,  70,  50, 25,  8, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('CHG-65W-003',   '65 W dual-port GaN charger',       'CHARGER',  'Laptop-capable PD, USB-C and USB-A ports, dual voltage',                     'Voltra', 149.00,  81.00,  45,  66,  18, 20, 12, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('CHG-CAR-004',   'Dual-port car charger',            'CHARGER',  '12/24 V socket, USB-C PD and USB-A, aluminium housing',                      'Voltra',  55.00,  28.00,  10,  40,  36, 15,  7, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('CHG-GAN-005',   '100 W GaN travel adapter',         'CHARGER',  'Three ports, 100 W laptop charging, dual voltage for travel',                 'Voltra', 199.00, 108.00,  72,  95,  84, 20, 14, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('CBL-CC-001',    'USB-C to USB-C cable',             'CABLE',    '1 m braided cable, 60 W PD charging, E-marked',                              'Kestrel', 25.00,  11.00, 180, 260, 150, 40,  4, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('CBL-AC-002',    'USB-A to USB-C cable',             'CABLE',    '1.5 m cable, charging and USB 2.0 data, TPE jacket',                        'Kestrel', 19.00,   8.00, 120, 300, 210, 40,  4, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('CBL-LTG-003',   'USB-C to Lightning cable',         'CABLE',    '1 m MFi cable for compatible Lightning devices',                             'Kestrel', 49.00,  25.00,  30,  55,  24, 25,  9, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('CBL-HDMI-004',  '4K HDMI cable',                    'CABLE',    '2 m HDMI 2.0 cable, 4K at 60 Hz, gold plated connectors',                    'Kestrel', 35.00,  17.00,  66,  80,  44, 30,  5, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('CBL-HDMI-205',  '8K HDMI 2.1 cable',                'CABLE',    '1.5 m HDMI 2.1 certified cable, 8K at 60 Hz',                                'Kestrel', 59.00,  31.00,  20,  48,  33, 20,  7, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('HUB-4USB-001',  'Four-port USB-A hub',              'HUB',      'USB 3.0, four USB-A data ports, bus powered',                                'Orbitek', 55.00,  28.00,  44,  60,  38, 20,  6, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('HUB-6IN1-002',  'Six-in-one USB-C hub',             'HUB',      'HDMI, USB-A, SD reader and USB-C PD passthrough',                            'Orbitek',139.00,  74.00,   9,  32,  26, 15, 10, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('HUB-8IN1-003',  'Eight-in-one USB-C dock',          'HUB',      'HDMI, Ethernet, SD, USB-A and 100 W PD passthrough',                         'Orbitek',239.00, 128.00,  28,  36,  17, 20, 14, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('HUB-ETH-004',   'USB-C Ethernet adapter',           'HUB',      'Gigabit Ethernet for USB-C laptops, aluminium body',                        'Orbitek', 89.00,  47.00,   0,  27,   6, 15,  8, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0),
  ('HUB-DOCK-005',  'Desktop charging dock',            'HUB',      '100 W desk dock with dual display output and 3 USB-A ports',                'Orbitek',329.00, 176.00,  35,  48,  31, 20, 16, TIMESTAMPADD(DAY, -60, '2026-10-27 12:00:00'), 0)
ON DUPLICATE KEY UPDATE sku = sku;


-- -------------------------------------------------------------------------------------
-- 2. SALES LEDGER - curated rows, one per edge case we want visible in the UI
--    quantity 1 / bulk 500 / discounted / priced above catalog / same-day / out of window
-- -------------------------------------------------------------------------------------
INSERT INTO sales (reference, product_sku, customer, warehouse, quantity, unit_price, sold_at)
VALUES
  -- the three sales that emptied MOU-WIRED-001 at every branch
  ('SEED-SAL-EDGE-001', 'MOU-WIRED-001', 'Bin Hamood Electronics', 'ABU_DHABI',  40,  29.00, TIMESTAMPADD(DAY, -1, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-002', 'MOU-WIRED-001', 'Emirates Tech Hub',       'DUBAI',      85,  29.00, TIMESTAMPADD(DAY, -3, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-003', 'MOU-WIRED-001', 'Sharaf DG',               'SHARJAH',    25,  29.00, TIMESTAMPADD(DAY, -5, '2026-10-27 12:00:00')),
  -- a branch emptied while the other two stay healthy
  ('SEED-SAL-EDGE-004', 'MOU-24G-003',   'LuLu Hypermarket',        'SHARJAH',    96,  59.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-005', 'KEY-LAPTOP-005','Virgin Megastore',        'ABU_DHABI',  18,  69.00, TIMESTAMPADD(DAY, -4, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-006', 'HUB-ETH-004',  'Amazon.ae',               'ABU_DHABI',  31,  89.00, TIMESTAMPADD(DAY, -6, '2026-10-27 12:00:00')),
  -- discounted sale: 99.00 against a 119.00 catalog price
  ('SEED-SAL-EDGE-007', 'MOU-GAME-004',  'Jarir Bookstore',         'SHARJAH',    22,  99.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  -- bulk sale with a volume discount: 22.75 against a 25.00 catalog price
  ('SEED-SAL-EDGE-008', 'CBL-CC-001',    'Noon Retail',             'DUBAI',     500,  22.75, TIMESTAMPADD(DAY, -8, '2026-10-27 12:00:00')),
  -- sold above the catalog price (premium/grey market stock)
  ('SEED-SAL-EDGE-009', 'KEY-MECH-003',  'Luxe Peripherals LLC',    'DUBAI',       4, 229.00, TIMESTAMPADD(DAY, -11, '2026-10-27 12:00:00')),
  -- single unit, sold today
  ('SEED-SAL-EDGE-010', 'CBL-AC-002',    'Al-Futtaim Electronics',  'DUBAI',       1,  19.00, TIMESTAMPADD(HOUR, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-011', 'CHG-65W-003',   'Virgin Megastore',        'DUBAI',      12, 149.00, TIMESTAMPADD(DAY, -1, '2026-10-27 12:00:00')),
  -- walk-in trade customer, sold today
  ('SEED-SAL-EDGE-012', 'MOU-24G-003',   'Walk-in customer',        'ABU_DHABI',   1,  59.00, TIMESTAMPADD(HOUR, -3, '2026-10-27 12:00:00')),
  -- outside the trailing 30-day window: this is the ONLY sale for HUB-DOCK-005 @ SHARJAH,
  -- so a plan for that pair shows the "no sales recorded in the baseline window" warning
  ('SEED-SAL-EDGE-013', 'HUB-DOCK-005',  'Emirates Tech Hub',       'SHARJAH',     3, 329.00, TIMESTAMPADD(DAY, -55, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-014', 'HUB-8IN1-003',  'Sharaf DG',               'ABU_DHABI',   9, 239.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-015', 'CHG-GAN-005',   'Amazon.ae',               'DUBAI',       7, 199.00, TIMESTAMPADD(DAY, -3, '2026-10-27 12:00:00')),
  -- rows below drive each low-stock branch down to its intended balance
  ('SEED-SAL-EDGE-016', 'KEY-BT-002',    'Noon Retail',             'SHARJAH',    20, 129.00, TIMESTAMPADD(DAY, -1, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-017', 'MOU-BT-002',    'LuLu Hypermarket',        'ABU_DHABI',  12,  79.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-018', 'KEY-MECH-003',  'Bin Hamood Electronics',  'ABU_DHABI',  18, 219.00, TIMESTAMPADD(DAY, -3, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-019', 'CHG-30W-002',   'Virgin Megastore',        'ABU_DHABI',  10,  75.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-020', 'CBL-LTG-003',   'Jarir Bookstore',         'SHARJAH',    15,  49.00, TIMESTAMPADD(DAY, -1, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-021', 'MOU-ERG-005',   'Sharaf DG',               'ABU_DHABI',  15,  99.00, TIMESTAMPADD(DAY, -1, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-022', 'MOU-ERG-005',   'Sharaf DG',               'DUBAI',      15,  99.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-023', 'MOU-ERG-005',   'Sharaf DG',               'SHARJAH',    15,  99.00, TIMESTAMPADD(DAY, -3, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-024', 'CBL-HDMI-205',  'Emirates Tech Hub',       'ABU_DHABI',  14,  59.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-025', 'CHG-CAR-004',   'Al-Futtaim Electronics',  'ABU_DHABI',  12,  55.00, TIMESTAMPADD(DAY, -1, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-026', 'HUB-6IN1-002',  'Amazon.ae',               'ABU_DHABI',  14, 139.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-027', 'KEY-BT-002',    'Noon Retail',             'ABU_DHABI',  21, 129.00, TIMESTAMPADD(DAY, -4, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-028', 'HUB-ETH-004',   'LuLu Hypermarket',        'SHARJAH',    14,  89.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-029', 'CHG-65W-003',   'Virgin Megastore',        'SHARJAH',    16, 149.00, TIMESTAMPADD(DAY, -1, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-030', 'MOU-GAME-004',  'Sharaf DG',               'ABU_DHABI',  18, 119.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-031', 'MOU-GAME-004',  'Sharaf DG',               'DUBAI',      26, 119.00, TIMESTAMPADD(DAY, -3, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-032', 'MOU-BT-002',    'LuLu Hypermarket',        'SHARJAH',     9,  79.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-033', 'KEY-WIRED-001', 'Bin Hamood Electronics',  'SHARJAH',    16,  49.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-034', 'KEY-COMBO-004', 'Noon Retail',             'ABU_DHABI',  11, 149.00, TIMESTAMPADD(DAY, -3, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-035', 'CHG-20W-001',   'Sharaf DG',               'SHARJAH',    22,  45.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-036', 'HUB-4USB-001',  'LuLu Hypermarket',        'DUBAI',      13,  55.00, TIMESTAMPADD(DAY, -4, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-037', 'CBL-HDMI-004',  'Virgin Megastore',        'ABU_DHABI',  20,  35.00, TIMESTAMPADD(DAY, -3, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-038', 'CHG-GAN-005',   'Jarir Bookstore',         'SHARJAH',     9, 199.00, TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-039', 'HUB-DOCK-005',  'Emirates Tech Hub',       'DUBAI',       6, 329.00, TIMESTAMPADD(DAY, -4, '2026-10-27 12:00:00')),
  ('SEED-SAL-EDGE-040', 'KEY-LAPTOP-005','Amazon.ae',               'SHARJAH',     9,  69.00, TIMESTAMPADD(DAY, -6, '2026-10-27 12:00:00'))
ON DUPLICATE KEY UPDATE reference = reference;


-- -------------------------------------------------------------------------------------
-- 3. REPLENISHMENT LEDGER - all six branch-to-branch directions, including chains
-- -------------------------------------------------------------------------------------
INSERT INTO replenishments (reference, product_sku, from_warehouse, to_warehouse, quantity, reason, moved_at)
VALUES
  ('SEED-MOV-001', 'MOU-BT-002',   'DUBAI',    'ABU_DHABI', 10,  'Branch replenishment for low Abu Dhabi stock',                       TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-MOV-002', 'MOU-BT-002',   'ABU_DHABI','DUBAI',      4,  'Return of overstocked units to the main warehouse',                     TIMESTAMPADD(DAY, -1, '2026-10-27 12:00:00')),
  ('SEED-MOV-003', 'KEY-BT-002',   'DUBAI',    'SHARJAH',   15,  'Top-up for the Sharjah counter',                                      TIMESTAMPADD(DAY, -4, '2026-10-27 12:00:00')),
  ('SEED-MOV-004', 'KEY-BT-002',   'SHARJAH',  'DUBAI',      5,  'Rebalancing after the weekend rush',                                  TIMESTAMPADD(DAY, -1, '2026-10-27 12:00:00')),
  ('SEED-MOV-005', 'CBL-CC-001',   'DUBAI',    'ABU_DHABI',120,  'Bulk cable replenishment from the Dubai warehouse',                     TIMESTAMPADD(DAY, -6, '2026-10-27 12:00:00')),
  ('SEED-MOV-006', 'CBL-CC-001',   'ABU_DHABI','SHARJAH',   40,  'Consolidating the Abu Dhabi buffer at Sharjah',                       TIMESTAMPADD(DAY, -3, '2026-10-27 12:00:00')),
  ('SEED-MOV-007', 'HUB-8IN1-003', 'DUBAI',    'SHARJAH',    8,  'Slow-moving docks moved to the Sharjah shelf',                        TIMESTAMPADD(DAY, -12, '2026-10-27 12:00:00')),
  ('SEED-MOV-008', 'CHG-65W-003',  'DUBAI',    'SHARJAH',   12,  'GaN charger cover for the Sharjah branch',                             TIMESTAMPADD(DAY, -2, '2026-10-27 12:00:00')),
  ('SEED-MOV-009', 'MOU-GAME-004', 'ABU_DHABI','DUBAI',      6,  'Gaming mouse consolidation into the Dubai warehouse',                   TIMESTAMPADD(DAY, -45, '2026-10-27 12:00:00')),
  ('SEED-MOV-010', 'HUB-ETH-004',  'SHARJAH',  'ABU_DHABI',  5,  'Ethernet adapters moved to the highest demand branch',                 TIMESTAMPADD(DAY, -5, '2026-10-27 12:00:00'))
ON DUPLICATE KEY UPDATE reference = reference;


-- -------------------------------------------------------------------------------------
-- 4. PLANNING SCENARIOS - premade saved what-if proposals
--    Start and inbound dates are fixed; live projections use actual recorded sales.
-- -------------------------------------------------------------------------------------
INSERT INTO planning_scenarios (id, product_sku, title, warehouse, disruption_type, start_date,
                                 demand_change_percent, additional_lead_time_days,
                                 planned_inbound_quantity, inbound_date, action_note, status,
                                 created_at, updated_at, version)
VALUES
  -- inbound arrives BEFORE the disrupted supplier lead time allows -> early-arrival warning
  (9001, 'MOU-WIRED-001', 'Mouse supplier delay in Abu Dhabi',    'ABU_DHABI', 'SUPPLIER_DELAY',  '2026-10-27',
        25.00,  7,  40, '2026-11-01',
        'Supplier delay: the proposed inbound lands before the disrupted lead time allows, so verify an alternative source or a branch transfer.',
        'CONFIRMED', TIMESTAMPADD(HOUR, -30, '2026-10-27 12:00:00'), TIMESTAMPADD(HOUR, -2, '2026-10-27 12:00:00'), 0),
  -- demand doubles against an almost empty branch and no inbound is assumed -> stockout
  (9002, 'MOU-GAME-004', 'Sharjah gaming mouse demand surge',      'SHARJAH',   'DEMAND_SURGE',   '2026-10-27',
       100.00,  0,   0, NULL,
        'Promotion surge: demand doubles against a branch that is nearly out of stock, and no inbound stock is assumed.',
        'DRAFT', TIMESTAMPADD(HOUR, -26, '2026-10-27 12:00:00'), TIMESTAMPADD(HOUR, -5, '2026-10-27 12:00:00'), 0),
  -- inbound on the final day of the rolling horizon
  (9003, 'CBL-CC-001',   'Cable transport disruption in Sharjah',  'SHARJAH',   'TRANSPORT_DELAY', '2026-10-27',
        40.00, 10,  60, '2026-11-25',
        'Transport disruption: the proposed arrival sits on the final day of the rolling horizon, so there is no buffer left afterwards.',
        'DRAFT', TIMESTAMPADD(HOUR, -20, '2026-10-27 12:00:00'), TIMESTAMPADD(HOUR, -1, '2026-10-27 12:00:00'), 0),
  -- demand drop to zero - a stress test, not a forecast
  (9004, 'HUB-8IN1-003', 'Dock demand drop in Abu Dhabi',          'ABU_DHABI', 'DEMAND_DROP',    '2026-10-27',
      -100.00,  3,   0, NULL,
        'Demand drop: zero demand is a stress-test assumption, not a forecast. Closing this scenario keeps it out of the active count.',
        'CLOSED', TIMESTAMPADD(HOUR, -72, '2026-10-27 12:00:00'), TIMESTAMPADD(HOUR, -40, '2026-10-27 12:00:00'), 0),
  -- no sales recorded for this SKU and branch in the baseline window
  (9005, 'HUB-DOCK-005', 'Dock supply review for Sharjah',         'SHARJAH',   'SUPPLIER_DELAY', '2026-10-27',
        30.00,  5,  25, '2026-11-20',
        'No sales were recorded for this SKU and branch in the last 30 days, so the baseline is zero and the projection is not evidence of no demand.',
        'CONFIRMED', TIMESTAMPADD(HOUR, -14, '2026-10-27 12:00:00'), TIMESTAMPADD(HOUR, -3, '2026-10-27 12:00:00'), 0),
  -- inbound batch scheduled inside the horizon, arriving after the lead time allows
  (9006, 'CHG-65W-003',  'GaN charger demand surge in Dubai',      'DUBAI',     'DEMAND_SURGE',   '2026-10-27',
        90.00,  0, 120, '2026-11-10',
        'Promotion surge: an inbound batch is scheduled inside the horizon, so compare the daily projection against the arrival date.',
        'DRAFT', TIMESTAMPADD(HOUR, -9, '2026-10-27 12:00:00'), TIMESTAMPADD(HOUR, -1, '2026-10-27 12:00:00'), 0),
  -- negative demand change with no inbound stock
  (9007, 'CBL-HDMI-004', 'Cable transport disruption in Abu Dhabi', 'ABU_DHABI', 'TRANSPORT_DELAY', '2026-10-27',
       -25.00,  4,   0, NULL,
        'Demand reduction: model a softer quarter while transport is constrained.',
        'CLOSED', TIMESTAMPADD(HOUR, -60, '2026-10-27 12:00:00'), TIMESTAMPADD(HOUR, -18, '2026-10-27 12:00:00'), 0),
  -- long supplier delay with a projected stockout before the inbound batch arrives
  (9008, 'KEY-MECH-003', 'Mechanical keyboard supplier delay',     'ABU_DHABI', 'SUPPLIER_DELAY', '2026-10-27',
        60.00, 14,  30, '2026-11-25',
        'Supplier delay: the inbound batch arrives close to the end of the horizon, so Abu Dhabi is exposed in the meantime.',
        'DRAFT', TIMESTAMPADD(HOUR, -6, '2026-10-27 12:00:00'), TIMESTAMPADD(HOUR, -2, '2026-10-27 12:00:00'), 0)
ON DUPLICATE KEY UPDATE id = id;
