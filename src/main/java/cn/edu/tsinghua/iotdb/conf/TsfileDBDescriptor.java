package cn.edu.tsinghua.iotdb.conf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.edu.tsinghua.tsfile.common.constant.SystemConstant;


public class TsfileDBDescriptor {
	private static final Logger LOGGER = LoggerFactory.getLogger(TsfileDBDescriptor.class);

	private static class TsfileDBDescriptorHolder{
		private static final TsfileDBDescriptor INSTANCE = new TsfileDBDescriptor();
	}
	
	private TsfileDBDescriptor() {
		loadProps();
	}

	public static final TsfileDBDescriptor getInstance() {
		return TsfileDBDescriptorHolder.INSTANCE;
	}

	public TsfileDBConfig getConfig() {
		return conf;
	}

	private TsfileDBConfig conf = new TsfileDBConfig();

	/**
	 * load an properties file and set TsfileDBConfig variables
	 *
	 */
	private void loadProps() {
		InputStream inputStream = null;
		String url = System.getProperty(TsFileDBConstant.IOTDB_CONF, null);
		if (url == null) {
			url = System.getProperty(SystemConstant.TSFILE_HOME, TsfileDBConfig.CONFIG_DEFAULT_PATH);
			if (!url.equals(TsfileDBConfig.CONFIG_DEFAULT_PATH)) {
				url = url + File.separatorChar + "conf" + File.separatorChar + TsfileDBConfig.CONFIG_NAME;
			}
		}
		try {
			inputStream = new FileInputStream(new File(url));
		} catch (FileNotFoundException e) {
			LOGGER.warn("Fail to find config file {}", url);
			return;
		}

		LOGGER.info("Start to read config file {}", url);
		Properties properties = new Properties();
		try {
			properties.load(inputStream);
			conf.rpcPort = Integer.parseInt(properties.getProperty("rpc_port",conf.rpcPort+""));
			
			conf.enableWal = Boolean.parseBoolean(properties.getProperty("enable_wal", conf.enableWal+""));

			conf.walCleanupThreshold = Integer.parseInt(properties.getProperty("wal_cleanup_threshold", conf.walCleanupThreshold+""));
			conf.flushWalThreshold = Integer.parseInt(properties.getProperty("flush_wal_threshold", conf.flushWalThreshold+""));
			conf.flushWalPeriodInMs = Integer.parseInt(properties.getProperty("flush_wal_period_in_ms", conf.flushWalPeriodInMs+""));
			
			conf.dataDir = properties.getProperty("data_dir", conf.dataDir);
			// update all data path
			conf.updateDataPath();
			
			conf.mergeConcurrentThreads = Integer.parseInt(properties.getProperty("merge_concurrent_threads", conf.mergeConcurrentThreads + ""));
			conf.maxOpenFolder = Integer.parseInt(properties.getProperty("max_opened_folder", conf.maxOpenFolder + ""));
			
			conf.fetchSize = Integer.parseInt(properties.getProperty("fetch_size", conf.fetchSize + ""));
			
			conf.periodTimeForFlush = Long.parseLong(properties.getProperty("period_time_for_flush_in_second", conf.periodTimeForFlush+"").trim());
			conf.periodTimeForMerge = Long.parseLong(properties.getProperty("period_time_for_merge_in_second", conf.periodTimeForMerge+"").trim());
			
			String tmpTimeZone = properties.getProperty("time_zone", conf.timeZone.getID());
			try {
				conf.timeZone = DateTimeZone.forID(tmpTimeZone.trim());
				LOGGER.info("Time zone has been set to {}", conf.timeZone);
			} catch (Exception e) {
				LOGGER.error("Time zone foramt error {}, use default configuration {}", tmpTimeZone, conf.timeZone);
			}

		} catch (IOException e) {
			LOGGER.warn("Cannot load config file because {}, use default configuration", e.getMessage());
		} catch (Exception e) {
			LOGGER.warn("Error format in config file because {}, use default configuration", e.getMessage());
		}
		if (inputStream != null) {
			try {
				inputStream.close();
			} catch (IOException e) {
				LOGGER.error("Fail to close config file input stream because {}", e.getMessage());
			}
		}
	}
}
