/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.mixed;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;

import org.junit.AfterClass;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class MixedDomainTestSuite {
    public enum Profile {
        FULL("full"), FULL_HA("full-ha"), DEFAULT("default");
        private final String profileName;
        Profile(String profileName) {
            this.profileName = profileName;
        }

        public String getProfile() {
            return profileName;
        }
    }
    private static MixedDomainTestSupport support;
    private static Version.AsVersion version;


    static Version.AsVersion getVersion(Class<?> testClass) {
        final Version version = testClass.getAnnotation(Version.class);
        if (version == null) {
            throw new IllegalArgumentException("No @Version");
        }
        if (MixedDomainTestSuite.version != null) {
            assertEquals(MixedDomainTestSuite.version, version.value());
        }
        MixedDomainTestSuite.version = version.value();
        return version.value();
    }

    /**
     * Call this from a @BeforeClass method
     *
     * @param testClass the test/suite class
     */
    protected static MixedDomainTestSupport getSupport(Class<?> testClass) {
            return getSupport(testClass, Profile.FULL_HA, false);
    }

    protected static MixedDomainTestSupport getSupport(Class<?> testClass,  boolean withPrimaryServers) {
            return getSupport(testClass, Profile.FULL_HA, withPrimaryServers);
    }

    protected static MixedDomainTestSupport getSupport(Class<?> testClass, Profile profile, boolean withPrimaryServers) {
        if (support == null) {
            final String copiedDomainXml = MixedDomainTestSupport.copyDomainFile();
            return getSupport(testClass, copiedDomainXml, profile, true, false, withPrimaryServers);
        }
        return support;
    }

    /**
     * Call this from a @BeforeClass method
     *
     * @param testClass the test/suite class
     */
    protected static MixedDomainTestSupport getSupport(Class<?> testClass, String primaryConfig, String secondaryConfig, Profile profile, boolean withPrimaryServers) {
        return getSupport(testClass, primaryConfig, secondaryConfig, profile, true, false, withPrimaryServers);
    }

    protected static MixedDomainTestSupport getSupport(Class<?> testClass, String primaryConfig, boolean adjustDomain, boolean legacyConfig, boolean withPrimaryServers) {
        return getSupport(testClass, primaryConfig, null, Profile.FULL_HA, adjustDomain, legacyConfig, withPrimaryServers);
    }

    /**
     * Call this from a @BeforeClass method
     *
     * @param testClass the test/suite class
     */
    protected static MixedDomainTestSupport getSupport(Class<?> testClass, String primaryConfig, String secondaryConfig, Profile profile, boolean adjustDomain, boolean legacyConfig, boolean withPrimaryServers) {
        if (support == null) {
            final String copiedDomainXml = MixedDomainTestSupport.copyDomainFile();
            return getSupport(testClass, copiedDomainXml, primaryConfig, secondaryConfig, profile, adjustDomain, legacyConfig, withPrimaryServers);
        }
        return support;
    }

    protected static MixedDomainTestSupport getSupport(Class<?> testClass, String primaryConfig, String secondaryConfig) {
        if (support == null) {
            final String copiedDomainXml = MixedDomainTestSupport.copyDomainFile();
            return getSupport(testClass, copiedDomainXml, primaryConfig, secondaryConfig, Profile.FULL_HA, true, false, false);
        }
        return support;
    }

    /**
     * Creates a MixedDomainTestSupport where the Domain Controller, which is the current WildFly version, is launched with the
     * domain configuration of the legacy Host Controller, for example EAP 8.0.0 or EAP 7.4.0.
     *
     * @param testClass the test/suite class
     * @param version the version of the legacy secondary.
     */
    protected static MixedDomainTestSupport getSupportForLegacyConfig(Class<?> testClass, Version.AsVersion version) {
        if (support == null) {
            final Path originalDomainXml = MixedDomainTestSupport.loadLegacyDomainXml(version);
            final String copiedDomainXml = MixedDomainTestSupport.copyDomainFile(originalDomainXml);
            return getSupport(testClass, copiedDomainXml, Profile.FULL_HA, true, true, false);
        }
        return support;
    }

    static MixedDomainTestSupport getSupport(Class<?> testClass, String domainConfig, Profile profile, boolean adjustDomain, boolean legacyConfig, boolean withPrimaryServers) {
        return getSupport(testClass, domainConfig, null, null, profile, adjustDomain, legacyConfig, withPrimaryServers);
    }

    static MixedDomainTestSupport getSupport(Class<?> testClass, String domainConfig, String primaryConfig, String secondaryConfig, Profile profile, boolean adjustDomain, boolean legacyConfig, boolean withPrimaryServers) {

        if (support == null) {
            final Version.AsVersion version = getVersion(testClass);
            final MixedDomainTestSupport testSupport;
            try {
                if (domainConfig != null) {
                    if(primaryConfig != null || secondaryConfig != null) {
                        testSupport = MixedDomainTestSupport.create(testClass.getSimpleName(), version, domainConfig, primaryConfig, secondaryConfig, profile.getProfile(),  adjustDomain, legacyConfig, withPrimaryServers);
                    } else {
                        testSupport = MixedDomainTestSupport.create(testClass.getSimpleName(), version, domainConfig, profile.getProfile(), adjustDomain, legacyConfig, withPrimaryServers);
                    }
                } else {
                    testSupport = MixedDomainTestSupport.create(testClass.getSimpleName(), version);
                }
            } catch (Exception e) {
                MixedDomainTestSuite.version = null;
                throw (e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e));
            }
            try {
                //Start the domain with adjustments to domain.xml
                testSupport.start();
                support = testSupport;
            } catch (Exception e) {
                testSupport.close();
                throw new RuntimeException(e);
            }
        }
        return support;
    }

    protected Version.AsVersion getVersion() {
        return version;
    }

    private static synchronized void stop() {
        version = null;
        if(support != null) {
            support.close();
            support = null;
        }
    }

    @AfterClass
    public static synchronized void afterClass() {
        stop();
    }
}
