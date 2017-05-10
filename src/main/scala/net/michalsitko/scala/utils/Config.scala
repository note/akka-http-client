package net.michalsitko.scala.utils

import com.typesafe.config.ConfigFactory

object Config {
  private val config = ConfigFactory.load()
  lazy val proxyHost: String = config.getString("httpsProxy.host")
  lazy val proxyPort: Int = config.getInt("httpsProxy.port")
}
