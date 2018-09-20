DROP INDEX [index_table1] ON [dbo].[table1]
GO

CREATE UNIQUE NONCLUSTERED INDEX [index_table1] ON [dbo].[table1] ([c1] ASC, [c2] DESC)
GO