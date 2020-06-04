package dinom.solr;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;
import static org.junit.Assert.*;


public class DateTest {

	@Test
	public void dateTimeParse() {
		
		TimeZone dflt = TimeZone.getDefault(); 
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

		String[] samples = new String[] {
				"2014-02-09T08:00:00.000-04:00", null,
				"2016-12-30T23:59:59.999-05:00", null,
				"2015-11-30T17:33:23.019 EST", "2015-11-30T17:33:23.019-05:00",
				"1968-06-11T09:33:25.717+03", "1968-06-11T09:33:25.717+03:00",
				"2011-11-07 19:53:58.717+0300", "2011-11-07T19:53:58.717+03:00",
				"2011-11-17 19:53:58.717 NotRecognized", "2011-11-17T19:53:58.717Z",
				"2011-06-07 19:53:58.717 America/New_York", "2011-06-07T19:53:58.717-04:00",
				"2011-01-07 19:53:58.717 America/New_York", "2011-01-07T19:53:58.717-05:00",
				"2012-10-27 22:00:01,17+0300", "2012-10-27T22:00:01.017+03:00",
				"2013-10-27 22:10:01 IST", "2013-10-27T22:10:01.000+05:30",
				"2014-10-27 22:10:59+05:30", "2014-10-27T22:10:59.000+05:30",
				"2016-12-31 23:59:59.999 Europe/Copenhagen ", "2016-12-31T23:59:59.999+01:00",
				"2017-10-28 22:10:01Z", "2017-10-28T22:10:01.000Z"
		};
		try {
			for(String tmz : new String[] {"GMT+03:00", "EST"} ) {
				
				TimeZone.setDefault(TimeZone.getTimeZone(tmz));  // "Africa/Nairobi", "MSK"
				
				for(int i=0; i < samples.length; i+=2) {
					String src = samples[i], exp = samples[i+1];
					if(exp == null) exp = src;

					Calendar c = Util.parseDateTime(src);
										
					fmt.setTimeZone(c.getTimeZone());
					String res = fmt.format(c.getTime());
					
					assertEquals(tmz,exp,res);
				}
			}

		}
		finally {
			TimeZone.setDefault(dflt);
		}
	}
	
	@Test
	public void solrDate() throws ParseException {
		TimeZone dflt = TimeZone.getDefault();
		
		String[] samples = new String[] {
				"2014-02-09", "10:00:07", null, null,
				"2014-02-09", "09:01:02", null, null,
				"2014-02-09", "10:00 PM", "GMT", "2014-02-09T22:00:00Z",
				"2014-02-09", "12:00:00 PM", "GMT+03:00", "2014-02-09T09:00:00Z",
				"2014-02-09", "12:00:00 AM", "GMT+03:00", "2014-02-08T21:00:00Z",
				"2014-02-09", "12:00 AM", "EST", "2014-02-09T05:00:00Z",
		};
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss VV");
		try {
			for(String tmz : new String[] {"America/New_York"} ) {
				
				TimeZone timezone = TimeZone.getTimeZone(tmz);
				TimeZone.setDefault(timezone);

				for(int i=0; i < samples.length; i+=4) {
					String date = samples[i], time = samples[i+1], tz = samples[i+2], exp = samples[i+3];
					if(tz == null) exp = DateTimeFormatter.ISO_INSTANT.format(fmt.parse(date+"T"+time+" "+tmz));
					String res = Util.toSolrDate(date, time, tz);
					
					assertEquals(tmz,exp,res);
				}
			}
		}
		finally {
			TimeZone.setDefault(dflt);
		}
			

	}
}
