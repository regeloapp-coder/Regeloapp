-- Regélő app v1.4.1 – a fióktörlés csak a fájlokat is kezelő Edge Functionön keresztül történhet.
-- Az alkalmazás nem hívja ezt az RPC-t; közvetlen hozzáférés megkerülné a Storage-takarítást.
revoke execute on function public.delete_own_account() from public, anon, authenticated;
