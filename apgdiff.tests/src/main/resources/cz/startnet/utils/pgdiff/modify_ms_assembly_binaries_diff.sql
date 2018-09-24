DROP ASSEMBLY [Hi] WITH NO DEPENDENTS
GO

CREATE ASSEMBLY [Hi]
AUTHORIZATION [dbo]
FROM 0x4D5A900003, 0x4D5A900004
WITH PERMISSION_SET = SAFE
GO
ALTER ASSEMBLY [Hi] WITH VISIBILITY = OFF
GO