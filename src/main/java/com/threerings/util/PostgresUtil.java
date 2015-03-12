//
// ooo-db - a place for OOO database utilities
// Copyright (C) 2015 Three Rings Design, Inc., All Rights Reserved
// https://github.com/threerings/ooo-db/blob/master/LICENSE

package com.threerings.util;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.postgresql.jdbc2.optional.PoolingDataSource;

import com.samskivert.depot.ConnectionProvider;
import com.samskivert.depot.DataSourceConnectionProvider;
import com.samskivert.util.Config;
import com.samskivert.util.MissingPropertyException;

import static com.samskivert.util.PropertiesUtil.requireProperty;

/**
 * Postgres related utility methods.
 */
public class PostgresUtil
{
    /**
     * Creates a ConnectionProvider that communicates with a Postgres database server using
     * read-only and read-write connections via a connection pooled data source. The supplied
     * config must provide the following configuration:
     * <pre>
     * db.default.server = DBHOST
     * db.default.port = 5432
     * db.default.database = DATABASE
     * db.default.username = USERNAME
     * db.default.password = PASSWORD
     * db.readonly.maxconns = 1
     * db.readwrite.maxconns = 1
     * </pre>
     * It can optionally provide overrides for the server, port, database, username and password
     * fields for the readonly and readwrite conncetions:
     * <pre>
     * db.readonly.server = READSLAVE
     * db.readonly.username = READUSER
     * db.readonly.password = READPASS
     * db.readwrite.server = MASTER
     * ...
     * </pre>
     *
     * @param config used to obtain the configuration data.
     * @param projectId a unique identifier for the system creating this provider which will be
     * used to prefix the identifiers of the data source names (which are JVM global).
     */
    public static ConnectionProvider createPoolingProvider (Config config, String projectId)
    {
        return createPoolingProvider(config, projectId, "db");
    }

    /**
     * Creates a ConnectionProvider as in {@link #createPoolingProvider(Config,String)}, but uses
     * the supplied {@code prefix} in place of {@code db}.
     */
    public static ConnectionProvider createPoolingProvider (
        Config config, String projectId, String prefix)
    {
        final DataSource[] sources = new DataSource[2];
        String[] modes = new String[] { "readonly", "readwrite" };
        String defPrefix = prefix + ".default";
        for (int ii = 0; ii < sources.length; ii++) {
            String modePrefix = prefix + "." + modes[ii];
            try {
                // start with defaults, then apply overrides
                Properties props = config.getSubProperties(defPrefix);
                config.getSubProperties(modePrefix, props);
                PoolingDataSource source = new PoolingDataSource();
                source.setDataSourceName(projectId + "." + modes[ii]);
                source.setServerName(requireProperty(props, "server"));
                source.setDatabaseName(requireProperty(props, "database"));
                source.setPortNumber(Integer.parseInt(requireProperty(props, "port")));
                source.setUser(requireProperty(props, "username"));
                source.setPassword(requireProperty(props, "password"));
                source.setMaxConnections(Integer.parseInt(props.getProperty("maxconns", "1")));
                sources[ii] = source;
            } catch (MissingPropertyException mpe) {
                String key = mpe.getKey();
                throw new MissingPropertyException(
                    key, "Unable to locate required property '" + key + "' as '" +
                    defPrefix + "." + key + "' or '" + modePrefix + "." + key + "'.");
            }
        }
        return new DataSourceConnectionProvider("jdbc:postgresql", sources[0], sources[1]) {
            @Override public void shutdown () {
                if (_shutdown.getAndSet(true)) {
                    // Only shutdown the sources once; Postgres' pooling data source doesn't like
                    // multiple shutdown calls
                    return;
                }
                for (DataSource source : sources) {
                    ((PoolingDataSource)source).close();
                }
            }
            protected AtomicBoolean _shutdown = new AtomicBoolean();
        };
    }
}
