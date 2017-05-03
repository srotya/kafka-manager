/**
 * Copyright 2015 Yahoo Inc. Licensed under the Apache License, Version 2.0
 * See accompanying LICENSE file.
 */

package kafka.manager.base

import kafka.manager.model.CuratorConfig
import org.apache.curator.framework.{ CuratorFramework, CuratorFrameworkFactory }
import org.apache.curator.retry.BoundedExponentialBackoffRetry

import scala.util.Try
import javax.security.auth.Subject
import java.security.PrivilegedAction
import javax.security.auth.login.LoginContext
import org.apache.zookeeper.client.ZooKeeperSaslClient
import org.apache.curator.framework.api.ACLProvider

trait CuratorAwareActor extends BaseActor {

  protected def curatorConfig: CuratorConfig

  protected[this] val curator: CuratorFramework = getCurator(curatorConfig)
  log.info("Starting curator...")
  curator.start()

  protected def getCurator(config: CuratorConfig): CuratorFramework = {
    val kerberos = System.getProperty("kerberos.enabled");
    if (kerberos != null) {
      System.err.println("\n\nKerberos not null" + kerberos + "\n\n");
      System.setProperty("zookeeper.sasl.client", "true");
      var principal = "kafka";
      System.setProperty(ZooKeeperSaslClient.LOGIN_CONTEXT_NAME_KEY,
        "Client");
      System.setProperty("zookeeper.authProvider.1",
        "org.apache.zookeeper.server.auth.SASLAuthenticationProvider");
      var aclProvider = new SASLAclProvider(principal);
      CuratorFrameworkFactory.builder().aclProvider(aclProvider)
      .connectString(
        config.zkConnect)
        .retryPolicy(new BoundedExponentialBackoffRetry(config.baseSleepTimeMs, config.maxSleepTimeMs, config.zkMaxRetry))
        .build();
    } else {
      CuratorFrameworkFactory.newClient(
        config.zkConnect,
        new BoundedExponentialBackoffRetry(config.baseSleepTimeMs, config.maxSleepTimeMs, config.zkMaxRetry))
    }
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.info("Shutting down curator...")
    Try(curator.close())
    super.postStop()
  }
}

trait BaseZkPath {
  this: CuratorAwareActor =>

  protected def baseZkPath: String

  protected def zkPath(path: String): String = {
    require(path.nonEmpty, "path must be nonempty")
    "%s/%s" format (baseZkPath, path)
  }

  protected def zkPathFrom(parent: String, child: String): String = {
    require(parent.nonEmpty, "parent path must be nonempty")
    require(child.nonEmpty, "child path must be nonempty")
    "%s/%s" format (parent, child)
  }
  
}
