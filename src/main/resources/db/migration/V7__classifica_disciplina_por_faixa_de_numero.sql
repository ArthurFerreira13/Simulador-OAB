-- V7__classifica_disciplina_por_faixa_de_numero.sql

UPDATE questoes q
SET disciplina_id = d.id
    FROM disciplinas d
WHERE d.nome = CASE
    WHEN q.numero BETWEEN 1  AND 8  THEN 'Ética e Estatuto da Advocacia'
    WHEN q.numero BETWEEN 9  AND 10 THEN 'Filosofia do Direito'
    WHEN q.numero BETWEEN 11 AND 16 THEN 'Constitucional'
    WHEN q.numero BETWEEN 17 AND 18 THEN 'Direitos Humanos'
    WHEN q.numero BETWEEN 19 AND 20 THEN 'Eleitoral'
    WHEN q.numero BETWEEN 21 AND 22 THEN 'Internacional'
    WHEN q.numero BETWEEN 23 AND 24 THEN 'Financeiro'
    WHEN q.numero BETWEEN 25 AND 29 THEN 'Tributário'
    WHEN q.numero BETWEEN 30 AND 34 THEN 'Administrativo'
    WHEN q.numero BETWEEN 35 AND 36 THEN 'Ambiental'
    WHEN q.numero BETWEEN 37 AND 42 THEN 'Civil'
    WHEN q.numero BETWEEN 43 AND 44 THEN 'da Criança e do Adolescente'
    WHEN q.numero BETWEEN 45 AND 46 THEN 'do Consumidor'
    WHEN q.numero BETWEEN 47 AND 50 THEN 'Empresarial'
    WHEN q.numero BETWEEN 51 AND 56 THEN 'Processual Civil'
    WHEN q.numero BETWEEN 57 AND 62 THEN 'Penal'
    WHEN q.numero BETWEEN 63 AND 68 THEN 'Processual Penal'
    WHEN q.numero BETWEEN 69 AND 70 THEN 'Previdenciário'
    WHEN q.numero BETWEEN 71 AND 75 THEN 'do Trabalho'
    WHEN q.numero BETWEEN 76 AND 80 THEN 'Processual do Trabalho'
END;