package controllers

import com.typesafe.config.ConfigValueType

import java.util.UUID

import org.apache.commons.codec.binary.Base64

import play.api.Configuration
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.HeaderNames.WWW_AUTHENTICATE
import play.api.libs.Crypto
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Cookie
import play.api.mvc.Filter
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results.Unauthorized

import scala.collection.JavaConverters._
import scala.concurrent.Future
import javax.naming.ldap.LdapContext
import javax.naming.ldap.InitialLdapContext
import java.util.Hashtable
import java.util.HashMap
import javax.naming.Context

class BasicAuthenticationFilter(configurationFactory: => BasicAuthenticationFilterConfiguration) extends Filter {

  def apply(next: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] =
    if (configuration.enabled && isNotExcluded(requestHeader))
      checkAuthentication(requestHeader, next)
    else next(requestHeader)

  private def isNotExcluded(requestHeader: RequestHeader): Boolean =
    !configuration.excluded.exists(requestHeader.path matches _)

  private def checkAuthentication(requestHeader: RequestHeader, next: RequestHeader => Future[Result]): Future[Result] =
    if (isAuthorized(requestHeader)) addCookie(next(requestHeader))
    else unauthorizedResult

  private def isAuthorized(requestHeader: RequestHeader): Boolean = {
    var auth = requestHeader.headers.get(AUTHORIZATION);
    if (!auth.isEmpty) {
      var splits = auth.get.split("\\s");
      splits = new String(Base64.decodeBase64(splits(1))).split(":");
      var username = splits(0);
      var password = splits(1);
      if (!configuration.ldap) {
        requestHeader.headers.get(AUTHORIZATION).exists(expectedHeaderValues)
      } else {
        ldapAuth(username, password);
      }
    } else {
      requestHeader.cookies.get(COOKIE_NAME).exists(_.value == cookieValue)
    }
  }

  private def ldapAuth(username: String, password: String): Boolean = {
    System.out.println("\n\nChecking ldap auth\n\n");
    var env = new java.util.Hashtable[String, String]()
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, configuration.ldapUrl);
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, configuration.ldapPrefix + username + configuration.ldapDn);
    env.put(Context.SECURITY_CREDENTIALS, password);
    try {
      var ctx = new InitialLdapContext(env, null);
      true;
    } catch {
      case e: Exception => System.err.println("Login failed:" + username); false;
    }
  }

  private def addCookie(result: Future[Result]) =
    result.map(_.withCookies(cookie))

  private lazy val configuration = configurationFactory

  private lazy val unauthorizedResult =
    Future successful Unauthorized.withHeaders(WWW_AUTHENTICATE -> realm)

  private lazy val COOKIE_NAME = "play-basic-authentication-filter"

  private lazy val cookie = Cookie(COOKIE_NAME, cookieValue)

  private lazy val cookieValue =
    Crypto.sign(configuration.username + configuration.passwords)

  private lazy val expectedHeaderValues =
    configuration.passwords.map { password =>
      val combined = configuration.username + ":" + password
      val credentials = Base64.encodeBase64String(combined.getBytes)
      basic(credentials)
    }

  private def realm = basic(s"""realm=\"${configuration.realm}"""")

  private def basic(content: String) = s"Basic $content"
}

object BasicAuthenticationFilter {
  def apply() = new BasicAuthenticationFilter(
    BasicAuthenticationFilterConfiguration.parse(
      play.api.Play.current.configuration))

  def apply(configuration: => Configuration) = new BasicAuthenticationFilter(
    BasicAuthenticationFilterConfiguration parse configuration)
}

case class BasicAuthenticationFilterConfiguration(
  realm: String,
  enabled: Boolean,
  username: String,
  passwords: Set[String],
  ldap: Boolean,
  ldapUrl: String,
  ldapDn: String,
  ldapPrefix: String,
  excluded: Set[String])

object BasicAuthenticationFilterConfiguration {

  private val defaultRealm = "Application"
  private def credentialsMissingRealm(realm: String) =
    s"$realm: The username or password could not be found in the configuration."

  def parse(configuration: Configuration) = {

    val root = "basicAuthentication."
    def boolean(key: String) = configuration.getBoolean(root + key)
    def string(key: String) = configuration.getString(root + key)
    def seq(key: String) =
      Option(configuration.underlying getValue (root + key)).map { value =>
        value.valueType match {
          case ConfigValueType.LIST => value.unwrapped.asInstanceOf[java.util.List[String]].asScala
          case ConfigValueType.STRING => Seq(value.unwrapped.asInstanceOf[String])
          case _ => sys.error(s"Unexpected value at `${root + key}`, expected STRING or LIST")
        }
      }

    val enabled = boolean("enabled").getOrElse(false)

    val credentials: Option[(String, Set[String])] = for {
      username <- string("username")
      passwords <- seq("password")
    } yield (username, passwords.toSet)

    val (username, passwords) = {
      def uuid = UUID.randomUUID.toString
      credentials.getOrElse((uuid, Set(uuid)))
    }

    def realm(hasCredentials: Boolean) = {
      val realm = string("realm").getOrElse(defaultRealm)
      if (hasCredentials) realm
      else credentialsMissingRealm(realm)
    }

    val excluded = configuration.getStringSeq(root + "excluded")
      .getOrElse(Seq.empty)
      .toSet

    val ldap = boolean("ldap").getOrElse(false)

    val ldapUrl = string("ldapUrl").getOrElse("ldap://localhost:389");

    val ldapDn = string("ldapDn").getOrElse(",dc=example,dc=com");

    val ldapPrefix = string("ldapPrefix").getOrElse("cn=");

    BasicAuthenticationFilterConfiguration(
      realm(credentials.isDefined),
      enabled,
      username,
      passwords,
      ldap,
      ldapUrl,
      ldapDn,
      ldapPrefix,
      excluded)
  }
}