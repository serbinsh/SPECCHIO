package ch.specchio.factories;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.ListIterator;

import ch.specchio.eav_db.SQL_StatementBuilder;
import ch.specchio.plots.GonioPosition;
import ch.specchio.plots.GonioSamplingPoints;
import ch.specchio.spaces.MeasurementUnit;
import ch.specchio.spaces.RefPanelCalSpace;
import ch.specchio.spaces.ReferenceSpaceStruct;
import ch.specchio.spaces.SensorAndInstrumentSpace;
import ch.specchio.spaces.Space;
import ch.specchio.spaces.SpectralSpace;
import ch.specchio.types.Instrument;

/**
 * Class for creating and manipulating Space objects.
 */
public class SpaceFactory extends SPECCHIOFactory {
	
	private boolean match_only_sensor;
	private boolean match_only_sensor_and_unit;
	
	/** field by which to order lists of spaces */
	private String order_by = "date";
	
	
	/**
	 * Constructor. 
	 * 
	 * @param db_user		database account user name
	 * @param db_password	database account password
	 * 
	 * @throws SPECCHIOFactoryException	could not establish initial context
	 */
	public SpaceFactory(String db_user, String db_password, String ds_name) throws SPECCHIOFactoryException {

		super(db_user, db_password, ds_name);
		
	}
	
	
	/**
	 * Copy constructor. Construct a new factory that uses the same database connection
	 * as an existing factory.
	 * 
	 * @param factory	the existing factory
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public SpaceFactory(SPECCHIOFactory factory) throws SPECCHIOFactoryException {
		
		super(factory);
		
	}
	
	
	/**
	 * Add a spectrum to a space
	 */
	private void addSpectrumToSpace(
			ArrayList<Space> spaces,
			int instrumentation_factors_id,
			int sensor_id,
			int measurement_type_id
		) throws SPECCHIOFactoryException {
		
		boolean space_exists = false;
		Space ss = null;
		
		// check if there is already a space that matches the sensor, instrument and calibration
		ListIterator<Space> li = spaces.listIterator();
		while(li.hasNext() && space_exists == false)
		{
			ss = li.next();
			if(((RefPanelCalSpace)ss).matches(sensor_id, measurement_type_id))
			{
				space_exists = true;
			}	
		}
		
		if(space_exists == false)
		{
			// create a new space
			MeasurementUnit mu = getMeasurementUnit(measurement_type_id);
			ss = createRefPanelCalSpace(sensor_id, mu);
			
			// add new space to space list
			spaces.add(ss);
		}
		
		ss.add_unique_spectrum_id(instrumentation_factors_id);
		
	}
	
	
	/**
	 * Add a spectrum to a space.
	 */
	private void addSpectrumToSpace(
			ArrayList<Space> spaces,
			int spectrum_id,
			int sensor_id,
			int instrument_id,
			int calibration_id,
			int measurement_type_id
		) throws SPECCHIOFactoryException {
		
		boolean space_exists = false;
		Space ss = null;
		
		// check if there is already a space that matches the sensor, instrument and calibration
		ListIterator<Space> li = spaces.listIterator();
		while(li.hasNext() && space_exists == false)
		{
			if (match_only_sensor_and_unit)
			{
				ss = li.next();
				
				if(((SensorAndInstrumentSpace)ss).matches(sensor_id, measurement_type_id))
				{
					space_exists = true;
				}					
				
			}
			else if (match_only_sensor)
			{
				ss = li.next();
				
				if(((SensorAndInstrumentSpace)ss).matches(sensor_id))
				{
					space_exists = true;
				}					
				
			}
			else
			{
				ss = li.next();
				if(((SensorAndInstrumentSpace)ss).matches(instrument_id, sensor_id, calibration_id, measurement_type_id))
				{
					space_exists = true;
				}	
			}
		}
		
		if(space_exists == false)
		{
			// create a new space
			MeasurementUnit mu = getMeasurementUnit(measurement_type_id);
			ss = createSensorAndInstrumentSpace(sensor_id, instrument_id, calibration_id, mu);
			
			// add new space to space list
			spaces.add(ss);
		}
		
		ss.add_unique_spectrum_id(spectrum_id);
		
	}
	
	
	/**
	 * Helper method for getSpaces() and loadSpace().
	 * 
	 * @param table		the table to be queried
	 * @param id_column	the primary key of the table
	 * @param columns	the columns to return (not including the id column)
	 * @param ids		the ids for which to return data
	 * @param order_by	the attribute to order by
	 *
	 * @return an SQL query that will return the data described by the input parameters
	 */
	private String buildSpaceQuery(String table, String id_column, String columns[], ArrayList<Integer> ids, String order_by) {
		
		// start with an empty string
		StringBuffer query = new StringBuffer();
		
		// select the appropriate columns from the table
		query.append("select ");
		for (int i = 0; i < columns.length; i++) {
			if (i > 0) {
				query.append(", ");
			}
			query.append(getStatementBuilder().prefix(table, columns[i]));
		}
		query.append(" from " + table);
		
		int order_by_attribute_id = 0;
		String order_by_storage_field = null;
		if (order_by != null) {
			// join to the attribute by which ordering will be done
			order_by_attribute_id = getAttributes().get_attribute_id(order_by);
			order_by_storage_field = getAttributes().get_default_storage_field(order_by_attribute_id);
			query.append(
				" left join (" +
					"select spectrum_x_eav.spectrum_id, eav.eav_id, eav." + order_by_storage_field + " " +
					"from spectrum_x_eav, eav " +
					"where spectrum_x_eav.eav_id = eav.eav_id " +
						"and eav.attribute_id = " + order_by_attribute_id +
				") t " +
				"on " +
					getStatementBuilder().prefix("t", id_column) +
					"=" +
					getStatementBuilder().prefix(table, id_column)
			);
		}
		
		// add id restriction
		query.append(" where " + getStatementBuilder().prefix(table, id_column) + " in (" + getStatementBuilder().conc_ids(ids) + ")");
		
		if (order_by != null) {
			// add order-by clause
			query.append(" order by " + getStatementBuilder().prefix("t", order_by_storage_field));
		}
		
		return query.toString();
		
	}
	
	
	/**
	 * Create a reference panel calibration space.
	 * 
	 * @param sensor_id			the sensor identifier
	 * @param mu				the measurement unit
	 * 
	 * @return a new RefPanelCalSpace object
	 *
	 * @throws SPECCHIOFactoryException	database error
	 */
	private RefPanelCalSpace createRefPanelCalSpace(int sensor_id, MeasurementUnit mu) throws SPECCHIOFactoryException {
		
		RefPanelCalSpace rpcs = new RefPanelCalSpace(sensor_id, mu);
		setSensorAndInstrumentInSpace(rpcs);
		return rpcs;		
	}
	
	
	/**
	 * Create a new sensor and instrument space.
	 * 
	 * @param sensor_id			the sensor identifier
	 * @param instrument_id		the instrument identifier (0 to create a reference panel calibration space)
	 * @param calibration_id	the calibration identifier
	 * @param mu				the measurement unit
	 * 
	 * @return a new SensorAndInstrumentSpace or RefPanelCalSpace object
	 *
	 * @throws SPECCHIOFactoryException	database error
	 */
	private SensorAndInstrumentSpace createSensorAndInstrumentSpace(
			int sensor_id,
			int instrument_id,
			int calibration_id,
			MeasurementUnit mu) throws SPECCHIOFactoryException {
		
		SensorAndInstrumentSpace sais;

		sais = new SensorAndInstrumentSpace(sensor_id, instrument_id, calibration_id, mu);
		sais.setOrderBy(this.order_by);

		setSensorAndInstrumentInSpace(sais);
		
		return sais;
	
	}
	
	
	/**
	 * Fill sensor and instrument information for a space.
	 * 
	 * @param sais	sensor and instrument space	
	 */	
	private void setSensorAndInstrumentInSpace(SensorAndInstrumentSpace sais)
	{
		
		try {
			sais.setSensor(getDataCache().get_sensor(sais.getSensorid()));
			sais.setInstrument(getDataCache().get_instrument(sais.getInstrumentId(), sais.getCalibrationId()));
			
//			if(sais.getInstrument() == null)
//			{
//				// try again without calibration id
//				sais.setInstrument(getDataCache().get_instrument(sais.getInstrumentId()));
//			}
			
			if(sais.getInstrument() == null)
			{
				// create a dummy instrument, only existing to provide empty attributes for reports
				sais.setInstrument(new Instrument()); 
				
				// set sensor so we can have a uniform call during the get_channels when plotting			
				sais.getInstrument().setSensor(sais.getSensor());
			}
			
			if(sais.getSensor() != null)
			{
				sais.setWvlsAreKnown(true);
				sais.setDimensionality(sais.getSensor().getNumberOfChannels().get_value());
//				sais.setSensorId(sensor_id);
//				sais.setInstrumentId(instrument_id);
//				sais.setCalibrationId(calibration_id);	
				sais.setAverageWavelengths(sais.getInstrument().getCentreWavelengths());
				
			} else {
				sais.setWvlsAreKnown(false);
			}
		} catch (SPECCHIOFactoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
	}
	
	
	/**
	 * Get calibration identifiers for a given set of spectrum identifiers
	 * 
	 * @param ssi_list
	 * @param ids		the spectrum identifiers
	 * 
	 * @return a list of spae_sorting_ident_struct objects
	 * 
	 * @throws SPECCHIOFactoryException	could not connect to the database
	 */
	public ArrayList<space_sorting_ident_struct> getCalibrationIds(
		ArrayList<space_sorting_ident_struct> ssi_list,
		ArrayList<Integer> ids) throws SPECCHIOFactoryException
	{
		
		int cal_id = 0;
		
		int cnt = 0;
	
		try {
			ResultSet rs;
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = getStatementBuilder().createStatement();
			String query =
					"select spectrum_id, calibration_id from (" +
						"select s.spectrum_id, s.instrument_id, c.calibration_id " +
						"from " +
							"spectrum_x_eav sxe, eav, attribute a, " +
							"spectrum s left outer join calibration c on c.instrument_id = s.instrument_id " + 
						"where " +
							"s.spectrum_id = sxe.spectrum_id and " +
							"sxe.eav_id = eav.eav_id and " +
							"a.attribute_id = eav.attribute_id and " + 
							"a.name = " + SQL.quote_string("Acquisition Time") + " and " +
							"eav.datetime_val >= c.calibration_date and " +
							"s.spectrum_id in (" + SQL.conc_ids(ids) + ") " +
						"order by (TIMEDIFF(eav.datetime_val, c.calibration_date)) asc " +
					") as closest_cal group by spectrum_id";

						
			rs = stmt.executeQuery(query);
			while (rs.next()) {
				
				int spectrum_id = rs.getInt(1);
				cal_id = rs.getInt(2);
				
				// find the right entry to store it
				// this is needed as the order of spectrum ids can change due to different order-by conditions
				boolean found = false;
				cnt = 0;
				
				while(!found && cnt < ssi_list.size())
				{
					if(ssi_list.get(cnt).spectrum_id == spectrum_id)
					{
						ssi_list.get(cnt).calibration_id = cal_id;
						found = true;
					}
					
					cnt++;
					
				}
			
			}
											
			stmt.close();
			rs.close();

			}catch (SQLException ex) {
				// database error
				throw new SPECCHIOFactoryException(ex);
			}
		
		return ssi_list;
		
	}
	
	
	/**
	 * Get an array of Space objects representing calibration factors.
	 * 
	 * @param spectrum_ids	the identifiers of the spectra to use
	 * 
	 * @return a list of Space objects representing the calibration factors corresponding to the input identifiers
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public ArrayList<Space> getCalibrationSpaces(ArrayList<Integer> spectrum_ids) throws SPECCHIOFactoryException {
		
		ArrayList<Integer> cal_factor_ids = new ArrayList<Integer>();
		ArrayList<Integer> handled_spectrum_ids = new ArrayList<Integer>();
		ArrayList<Space> cal_spaces = new ArrayList<Space>();
		
		try {
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			
			// output dimension is equal to the sensor wavelengths of the sensor
			// attached to the panel corr factors.
			// Therefore:
			// get the spectrum_ids of the corr factors that are referenced by the
			// correct calibration of the panel used for the spectra in the input space
			// 
			String query = "select s.spectrum_id, c.cal_factors from spectrum s, calibration c, reference r, spectrum_x_eav sxe, eav eav where " +
				"s.spectrum_id in (" + SQL.conc_ids(spectrum_ids) + ") and s.reference_id = r. reference_id and c.reference_id = r. reference_id " +
				" and s.spectrum_id = sxe.spectrum_id and eav.eav_id = sxe.eav_id and eav.attribute_id = (select attribute_id from attribute where name = 'Acquisition Time') "  +
				"and eav.datetime_val >= c.calibration_date order by s.spectrum_id, abs(TIMEDIFF(c.calibration_date, eav.datetime_val)) asc";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				int spectrum_id = rs.getInt(1);
				
				// only store the closest reference calibration data relative to spectrum acquisition date
				if(!handled_spectrum_ids.contains(spectrum_id))
				{					
					int cal_id = rs.getInt(2);
					if(!cal_factor_ids.contains(cal_id)) cal_factor_ids.add(cal_id);	
					handled_spectrum_ids.add(spectrum_id);
				}
				
				
			}	
			
			rs.close();		
			
			// create the spaces
			query = "SELECT instrumentation_factors_id, sensor_id, measurement_unit_id " +
					"from instrumentation_factors where instrumentation_factors_id in (" + SQL.conc_ids(cal_factor_ids) + ")";
			rs = stmt.executeQuery(query);
			while (rs.next()) {				
				addSpectrumToSpace(cal_spaces, rs.getInt(1), rs.getInt(2), rs.getInt(3));				
			}	
			rs.close();
			
			stmt.close();
			
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return cal_spaces;
		
	}
	
	
	/**
	 * Get the Goniometer sampling points for a space.
	 * 
	 * @param space	the space
	 * 
	 * @return a GonioSamplingPoints object
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public GonioSamplingPoints getGonioSamplingPoints(SpectralSpace space) throws SPECCHIOFactoryException {
		
		GonioSamplingPoints sampling_points = new GonioSamplingPoints(space);
		
		try {
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			String query;
			
			// get attribute identifiers for sensor zenith and sensor azimuth
			int zenith_id = getAttributes().get_attribute_id("Sensor Zenith");
			int azimuth_id = getAttributes().get_attribute_id("Sensor Azimuth");
			if (zenith_id == 0 || azimuth_id == 0) {
				// database does not support these attributes
				throw new SPECCHIOFactoryException("The sensor zenith and azimuth attributes are not available.");
			}
			

			// create a temporary table with one row for each spectrum
			String tempname = SQL.prefix(getTempDatabaseName(), "sampling_geometry_" + getDatabaseUserName());
			query = "create temporary table if not exists " + tempname + " (" +
						"spectrum_id INT(10) PRIMARY KEY NOT NULL, " +
						"zenith DOUBLE, " +
						"azimuth DOUBLE " +
					")";
			stmt.executeUpdate(query);
			query = "delete from " + tempname;
			stmt.executeUpdate(query);
			query = "insert into " + tempname + "(spectrum_id, zenith, azimuth) values(" +
						"?, " +
						"(select double_val from spectrum_x_eav, eav where " +
							"spectrum_x_eav.spectrum_id=? and " +
							"spectrum_x_eav.eav_id=eav.eav_id and " +
							"eav.attribute_id=" + Integer.toString(zenith_id) +
						"), " +
						"(select double_val from spectrum_x_eav, eav where " +
							"spectrum_x_eav.spectrum_id=? and " +
							"spectrum_x_eav.eav_id=eav.eav_id and " +
							"eav.attribute_id=" + Integer.toString(azimuth_id) +
						")" +
					")";
			PreparedStatement pstmt = SQL.prepareStatement(query);
			for (Integer spectrum_id : space.getSpectrumIds()) {
				pstmt.setInt(1, spectrum_id);
				pstmt.setInt(2, spectrum_id);
				pstmt.setInt(3, spectrum_id);
				pstmt.executeUpdate();
			}
			pstmt.close();
			
			// build the list of sensor angles from the temporary table
			query = "select spectrum_id, zenith, azimuth from " + tempname;
			ResultSet rs = stmt.executeQuery(query);
			int s = 0;
			while (rs.next()) 
			{	
				int spectrum_id = rs.getInt(1);
				float sensor_zenith = rs.getFloat(2);
				float sensor_azimuth  = rs.getFloat(3);
				float sensor_elevation = 90 - sensor_zenith;
					
				double alpha = sensor_azimuth*(Math.PI/180);
				double beta = sensor_elevation*(Math.PI/180); 
								
				double x = Math.sin(alpha)*Math.cos(beta);
				double y = Math.cos(alpha)*Math.cos(beta);
					
				sampling_points.setAngle(s, new GonioPosition(sensor_azimuth, sensor_zenith, x, y));
					
				// store spectrum_ids in the correct order to match them with the internal index later
				sampling_points.setSpectrumId(s, spectrum_id);
					
				s++;
			}
			rs.close();
			
			// clean up
			stmt.close();

		} catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return sampling_points;
	}
	
	
	/**
	 * Get a measurement unit.
	 * 
	 * @param measurement_unit_id	the measurement unit identifier
	 * 
	 * @return a MeasurementUnit object corresponding to measurement_unit_id
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public MeasurementUnit getMeasurementUnit(int measurement_unit_id) throws SPECCHIOFactoryException {
		
		MeasurementUnit mu = new MeasurementUnit(measurement_unit_id);

		if (measurement_unit_id != 0) {
			SQL_StatementBuilder SQL = getStatementBuilder();	
			mu.setUnitNumber(Integer.parseInt(SQL.get_field_value("ASD_coding", "measurement_unit", measurement_unit_id)));
			mu.setUnitName(SQL.get_field_value("name", "measurement_unit", measurement_unit_id));
		}
			
		return mu;
	}
	
	
	/**
	 * Get a reference space.
	 * 
	 * @param input_ids		the identifiers of the input spectra
	 * @param output_ids	the identifier of the output spectra
	 * 
	 * @return a ReferenceSpaceStruct object
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public ReferenceSpaceStruct getReferenceSpace(ArrayList<Integer> input_ids) throws SPECCHIOFactoryException {
		
		ReferenceSpaceStruct rss = new ReferenceSpaceStruct();
		
		try {
			
			SQL_StatementBuilder SQL = getStatementBuilder();
			String query;
			ResultSet rs;
			

			// check if we are linking to spectralon (radiance) or irradiance (cosine receptor) data
			query = "select count(*)" +
					" from spectrum_datalink where spectrum_id in (" + SQL.conc_ids(input_ids) + ") " +
					"and datalink_type_id = (select datalink_type_id from datalink_type where name = ?)";
			PreparedStatement pstmt = SQL.prepareStatement(query);

			int spectralon_no_of_rows = 0;	
			pstmt.setString(1, "Spectralon data");
			rs = pstmt.executeQuery();
			while (rs.next()) {
				spectralon_no_of_rows = rs.getInt(1);
			}

			int cosine_no_of_rows = 0;
			pstmt.setString(1, "Cosine receptor data");
			rs = pstmt.executeQuery();
			while (rs.next()) {
				cosine_no_of_rows = rs.getInt(1);
			}
			
			rss.is_spectralon = spectralon_no_of_rows > 0 && cosine_no_of_rows == 0;
			
			pstmt.close();
			
			// build the reference table
			ArrayList<Integer> spectralon_spectra_ids = new ArrayList<Integer>();
			Statement stmt = SQL.createStatement();
			String datalink = (rss.is_spectralon)? "Spectralon data"  : "Cosine receptor data";
			query = "select linked_spectrum_id, spectrum_id" +
					" from spectrum_datalink where spectrum_id in (" + SQL.conc_ids(input_ids) + ") " +
					"and datalink_type_id = (select datalink_type_id from datalink_type where name = '" + datalink + "')";
			rs = stmt.executeQuery(query);
			while (rs.next()) 
			{
				Integer linked_id = rs.getInt(1);
				Integer spectrum_id = rs.getInt(2);

				// build hash table
				rss.spectrum_reference_table.put(spectrum_id, linked_id);
				rss.spectrum_ids.add(spectrum_id);
				spectralon_spectra_ids.add(linked_id);	
				
			}
			rs.close();
			stmt.close();

			rss.reference_space = null;
			if (spectralon_spectra_ids.size() > 0) {
				ArrayList<Space> spaces = getSpaces(spectralon_spectra_ids);
				if (spaces.size() == 1) {
					rss.reference_space = (SpectralSpace)spaces.get(0);
					loadSpace(rss.reference_space);
				}
			}
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return rss;
		
	}
	
	
	/**
	 * Get Space objects for a given set of spectra.
	 * 
	 * @param spectrum_ids	a list of spectrum identifiers
	 * 
	 * @return an array of Space objects corresponding to the given identifiers
	 * 
	 * @throws SPECCHIOFactoryException	could not access the database
	 */
	public ArrayList<Space> getSpaces(ArrayList<Integer> spectrum_ids) throws SPECCHIOFactoryException {
		
		ArrayList<Space> spaces = new ArrayList<Space>();
		ArrayList<space_sorting_ident_struct> ssi_list = new ArrayList<space_sorting_ident_struct>();
		
		try {
			
			SQL_StatementBuilder SQL = getStatementBuilder();
			
			// create a query string
			String columns[] = new String[] {
				"spectrum_id",
				"sensor_id",
				"instrument_id",
				"measurement_unit_id",
				"calibration_id"
			};
			String query = buildSpaceQuery("spectrum", "spectrum_id", columns, spectrum_ids, this.order_by);
			
			// get the spectra from the database
			Statement stmt = SQL.createStatement();
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				space_sorting_ident_struct ssi = new space_sorting_ident_struct();
				ssi.spectrum_id = rs.getInt(1);
				ssi.sensor_id = rs.getInt(2);
				ssi.instrument_id = rs.getInt(3);
				ssi.measurement_unit_id = rs.getInt(4);		
				ssi.calibration_id = rs.getInt(5);	
				ssi_list.add(ssi);				
			}	
			rs.close();
			stmt.close();
			
			
		} catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
		//ssi_list = getCalibrationIds(ssi_list, spectrum_ids);
		
		//int spectrum_id, int sensor_id, int instrument_id, int calibration_id, int measurement_type_id
		
		ListIterator<space_sorting_ident_struct> li = ssi_list.listIterator();
		while(li.hasNext())
		{
			space_sorting_ident_struct ssi = li.next();
			addSpectrumToSpace(spaces, ssi.spectrum_id, ssi.sensor_id, ssi.instrument_id, ssi.calibration_id, ssi.measurement_unit_id);

		}
		
		return spaces;
		
	}
	
	
	/**
	 * Get a set of spaces corresponding to instrumentation factors.
	 * 
	 * @param instrumentation_factor_ids	the instrumentation factor identifiers
	 * 
	 * @return a list of Space objects corresponding to the given identifiers
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public ArrayList<Space> getSpacesForInstrumentationFactors(ArrayList<Integer> instrumentation_factor_ids)
		throws SPECCHIOFactoryException {
		
		ArrayList<Space> spaces = new ArrayList<Space>(instrumentation_factor_ids.size());
		
		try {
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			String query = "SELECT instrumentation_factors_id, sensor_id, measurement_unit_id from instrumentation_factors " +
					"where instrumentation_factors_id in (" + SQL.conc_ids(instrumentation_factor_ids) + ")";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				addSpectrumToSpace(spaces, rs.getInt(1), rs.getInt(2), rs.getInt(3));
			}
			rs.close();
			stmt.close();
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return spaces;
		
	}
	
	
	/**
	 * Load a Space object from the database.
	 * 
	 * @param space	a partially-filled Space object
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void loadSpace(Space space) throws SPECCHIOFactoryException {
			
			// clear existing data vectors
			space.clearDataVectors();
	
			try {
				// create SQL-building objects
				SQL_StatementBuilder SQL = getStatementBuilder();
				Statement stmt = SQL.createStatement();
				
				String quicker_order_by = "";
				
				// build query
				String table;
				String id_column;
				String order_by;
				if (space instanceof RefPanelCalSpace) {
					// load instrumentation calibration factors
					table = "instrumentation_factors";
					id_column = "instrumentation_factors_id";
					order_by = null;
				} else {
					// load spectral data
					table = "spectrum";
					id_column = "spectrum_id";
					//order_by = space.getOrderBy();
					String conc_ids = SQL.conc_ids(space.getSpectrumIds());
					order_by = null;
					quicker_order_by = "order by FIELD (spectrum_id, "+ conc_ids +")";
				}
				String columns[] = new String[] { "measurement" };
				String query = buildSpaceQuery(table, id_column, columns, space.getSpectrumIds(), order_by) + quicker_order_by; 
				
				ResultSet rs = stmt.executeQuery(query);
				while (rs.next()) 
				{
					Blob measurement = rs.getBlob(1);
					InputStream binstream = measurement.getBinaryStream();
					DataInput dis = new DataInputStream(binstream);
					
					if(!space.getWvlsAreKnown() && space.getDimensionalityIsSet() == false)
					{
						try {
							space.setDimensionality(binstream.available() / 4);
						} catch (IOException e) {
							// dont't know what would cause this
							e.printStackTrace();
						}
					}
					
					double[] vector = new double[space.getDimensionality()];
					
					for(int i = 0; i < space.getDimensionality(); i++)
					{
						try {
							Float f = dis.readFloat();
							vector[i] = f.doubleValue();
						} catch (IOException e) {
							// don't know what would cause this
							e.printStackTrace();
						}				
					}
					
					try {
						binstream.close();
					} catch (IOException e) {
						// don't know what would cause this
						e.printStackTrace();
					}
					
					space.addVector(vector);
		
				}
				
				rs.close();	
				stmt.close();
			} catch (SQLException ex) {
				// database error
				throw new SPECCHIOFactoryException(ex);
			}
	}
	
	
	public void setMatchOnlySensor(boolean match_only_sensor) {
		
		this.match_only_sensor = match_only_sensor;
		
	}
	
	
	public void setMatchOnlySensorAndUnit(boolean match_only_sensor_and_unit) {
		
		this.match_only_sensor_and_unit = match_only_sensor_and_unit;
		
	}
	
	
	/**
	 * Set the attribute by which spaces will be ordered.
	 * 
	 * @param order_by	the name of the attribute by which results will be ordered
	 */
	public void setOrderByAttribute(String order_by) {
		
		this.order_by = order_by;
		
	}

}
