package com.twitter.server.model

/**
 * ClientProfile is used to represent data
 * displayed in the "Least Performant
 * Downstream Clients" section of the Twitter
 * Server Admin Interface.
 */
private[server] case class ClientProfile(
  name: String,
  addr: String,
  scope: String,
  successRate: Double,
  unavailable: Int)
