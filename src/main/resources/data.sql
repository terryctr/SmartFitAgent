-- Initial seed data for SmartFitAgent
-- Fitness challenges
INSERT IGNORE INTO challenges (id, title, description, challenge_type, target_value, target_unit, start_date, end_date, status, reward_points)
VALUES ('ch001', '30天深蹲挑战', '坚持30天每日深蹲，从50个开始逐渐增加到100个，提升腿部力量和核心稳定性', 'STRENGTH', 30, '天', CURRENT_DATE, DATEADD(DAY, 30, CURRENT_DATE), '进行中', 100);
INSERT IGNORE INTO challenges (id, title, description, challenge_type, target_value, target_unit, start_date, end_date, status, reward_points)
VALUES ('ch002', '每日万步挑战', '连续21天每天完成10000步以上，养成有氧运动习惯，提高心肺功能', 'CARDIO', 21, '天', CURRENT_DATE, DATEADD(DAY, 21, CURRENT_DATE), '进行中', 80);
INSERT IGNORE INTO challenges (id, title, description, challenge_type, target_value, target_unit, start_date, end_date, status, reward_points)
VALUES ('ch003', '蛋白质摄入挑战', '连续14天每日蛋白质摄入达到体重(kg)×1.6g，支持肌肉合成与修复', 'NUTRITION', 14, '天', CURRENT_DATE, DATEADD(DAY, 14, CURRENT_DATE), '进行中', 60);
INSERT IGNORE INTO challenges (id, title, description, challenge_type, target_value, target_unit, start_date, end_date, status, reward_points)
VALUES ('ch004', '早起晨练挑战', '连续7天在早上7点前完成20分钟晨间锻炼，培养晨练习惯', 'HABIT', 7, '天', CURRENT_DATE, DATEADD(DAY, 7, CURRENT_DATE), '进行中', 50);
INSERT IGNORE INTO challenges (id, title, description, challenge_type, target_value, target_unit, start_date, end_date, status, reward_points)
VALUES ('ch005', '无糖饮食挑战', '坚持7天不摄入添加糖，减少精制碳水化合物，观察身体变化', 'NUTRITION', 7, '天', CURRENT_DATE, DATEADD(DAY, 7, CURRENT_DATE), '进行中', 40);

-- Sample quiz questions (fitness/health knowledge)
INSERT IGNORE INTO questions (id, subject, stem, option_a, option_b, option_c, option_d, answer, explanation)
VALUES ('q001', '健身基础', 'BMI(体质指数)的计算公式是什么？', '体重(kg) ÷ 身高(m)', '体重(kg) ÷ 身高²(m²)', '体重(kg) × 身高(m)', '体重(kg) ÷ 身高(cm)', 'B', 'BMI = 体重(kg) / 身高²(m²)，正常范围为18.5-23.9');
INSERT IGNORE INTO questions (id, subject, stem, option_a, option_b, option_c, option_d, answer, explanation)
VALUES ('q002', '训练原则', '渐进超负荷原则是指什么？', '每次训练前进行热身', '逐渐增加训练难度、重量或容量', '训练后需要充分恢复', '不同部位轮流训练', 'B', '渐进超负荷是肌肉增长的核心原则，通过持续增加刺激使肌肉适应并生长');
INSERT IGNORE INTO questions (id, subject, stem, option_a, option_b, option_c, option_d, answer, explanation)
VALUES ('q003', '营养知识', '一克蛋白质含有多少千卡热量？', '4千卡', '7千卡', '9千卡', '2千卡', 'A', '蛋白质和碳水化合物均含4千卡/克，脂肪含9千卡/克，酒精含7千卡/克');
INSERT IGNORE INTO questions (id, subject, stem, option_a, option_b, option_c, option_d, answer, explanation)
VALUES ('q004', '恢复训练', 'DOMS(延迟性肌肉酸痛)通常在训练后几小时出现？', '立即出现', '6-12小时后', '24-72小时后', '1周后', 'C', 'DOMS通常在训练后24-48小时达到峰值，是肌肉微损伤修复过程的正常反应');
INSERT IGNORE INTO questions (id, subject, stem, option_a, option_b, option_c, option_d, answer, explanation)
VALUES ('q005', '有氧训练', 'Zone 2有氧训练的目标心率范围是最大心率的多少？', '50-60%', '60-70%', '70-80%', '80-90%', 'B', 'Zone 2训练心率为最大心率的60-70%，能有效提升有氧基础和脂肪氧化能力');
