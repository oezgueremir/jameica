/**********************************************************************
 * $Source: /cvsroot/jameica/jameica/src/de/willuhn/jameica/security/JameicaTrustManager.java,v $
 * $Revision: 1.2 $
 * $Date: 2005/02/26 18:14:59 $
 * $Author: web0 $
 * $Locker:  $
 * $State: Exp $
 *
 * Copyright (c) by willuhn.webdesign
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.security;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;


/**
 * Unser eigener TrustManager fuer die Zertifikatspruefung zwischen Client und Server.
 */
public class JameicaTrustManager implements X509TrustManager
{
  private X509TrustManager standardTrustManager = null;

  /**
   * ct.
   * @throws KeyStoreException
   * @throws Exception
   */
  public JameicaTrustManager() throws KeyStoreException, Exception
  {
    super();
		// Wir ermitteln den System-TrustManager.
		// Alles was wir nicht wollen, delegieren wir an ihn weiter.
    TrustManagerFactory factory = TrustManagerFactory.getInstance("SunX509");
    factory.init(Application.getSSLFactory().getKeyStore());
    TrustManager[] trustmanagers = factory.getTrustManagers();
    if (trustmanagers.length == 0)
    {
      throw new NoSuchAlgorithmException("SunX509 trust manager not supported");
    }
    this.standardTrustManager = (X509TrustManager) trustmanagers[0];
  }

  /**
   * @see javax.net.ssl.X509TrustManager#checkClientTrusted(java.security.cert.X509Certificate[], java.lang.String)
   */
  public void checkClientTrusted(X509Certificate[] chain, String authType)
    throws CertificateException
  {
  	Logger.info("checking client certificate");
		//this.standardTrustManager.checkClientTrusted(chain,authType);
		// TODO JAMEICA Zertifikatspruefung einbauen.
    return;
  }

  /**
   * @see javax.net.ssl.X509TrustManager#checkServerTrusted(java.security.cert.X509Certificate[], java.lang.String)
   */
  public void checkServerTrusted(X509Certificate[] certificates, String arg1)
    throws CertificateException
  {
		Logger.info("checking server certificate");
		// TODO JAMEICA Zertifikatspruefung einbauen.
    return;
  }

  /**
   * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
   */
  public X509Certificate[] getAcceptedIssuers()
  {
		Logger.info("checking accecpted issuers");
    return this.standardTrustManager.getAcceptedIssuers();
  }
}

/**********************************************************************
 * $Log: JameicaTrustManager.java,v $
 * Revision 1.2  2005/02/26 18:14:59  web0
 * @N new nightly builds
 * @C readme file
 *
 * Revision 1.1  2005/01/19 02:14:00  willuhn
 * @N Wallet zum Verschluesseln von Benutzerdaten
 *
 * Revision 1.4  2005/01/19 01:00:39  willuhn
 * *** empty log message ***
 *
 * Revision 1.3  2005/01/15 16:20:32  willuhn
 * *** empty log message ***
 *
 * Revision 1.2  2005/01/12 00:59:38  willuhn
 * *** empty log message ***
 *
 * Revision 1.1  2005/01/12 00:17:17  willuhn
 * @N JameicaTrustManager
 *
 **********************************************************************/