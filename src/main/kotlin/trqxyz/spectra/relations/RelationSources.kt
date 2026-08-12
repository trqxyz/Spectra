package trqxyz.spectra.relations

class RelationSources(
  val vault: VaultEconomyBridge,
  val worldGuard: WorldGuardRelationProvider,
  val banStatus: BanStatusBridge,
  val ip: RelationIpCollector,
)
