package org.fao.geonet.services.statistics;

import com.google.common.base.Optional;
import jeeves.constants.Jeeves;
import jeeves.server.ServiceConfig;
import jeeves.server.context.ServiceContext;
import org.fao.geonet.domain.Metadata;
import org.fao.geonet.repository.MetadataRepository;
import org.fao.geonet.utils.IO;
import org.fao.geonet.utils.Log;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.services.NotInReadOnlyModeService;
import org.jdom.Element;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;
import org.springframework.data.jpa.domain.Specification;

import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Service to get the db-stored requests information group by source (node) id
 * displays a chart chowing the popularity per distinct node
 * todo: factorize chart code into a factory, with the GroupsPopularity services...
 * since there is not yet a solid config option mechanism for the graph, 2 identical classes
 * are easier to deal to change graph representation...
 * @author nicolas Ribot
 * @author Simon Pigot
 *
 */
public class CategoriesPopularity extends NotInReadOnlyModeService {
	/** should we generate and send tooltips to client (caution, can slow down the process if
	 * dataset is big)
	 */
	private boolean createTooltips;
	
	/** the imagemap for this chart, allowing to display tooltips */
	private String imageMap;
	/** should we generate and send legend to client (caution, can slow down the process if
	 * dataset is big)
	 */
//	private boolean createLegend;
	
	/** chart width, service parameter, can be overloaded by request */
	private int chartWidth;
	
	/** chart width, can be overloaded by request */
	private int chartHeight;
	
	
	//--------------------------------------------------------------------------
	//---
	//--- Init
	//---
	//--------------------------------------------------------------------------

	public void init(String appPath, ServiceConfig params) throws Exception	{
        super.init(appPath,  params);
//		this.createLegend = Boolean.parseBoolean(params.getValue("createLegend"));
		this.createTooltips = Boolean.parseBoolean(params.getValue("createTooltips"));
		this.chartWidth = Integer.parseInt(params.getValue("chartWidth"));
		this.chartHeight = Integer.parseInt(params.getValue("chartHeight"));
	}

	//--------------------------------------------------------------------------
	//---
	//--- Service
	//---
	//--------------------------------------------------------------------------
    @Override
	public Element serviceSpecificExec(Element params, ServiceContext context) throws Exception {
        String message = "";
		GeonetContext gc = (GeonetContext) context.getHandlerContext(Geonet.CONTEXT_NAME);

        final MetadataRepository metadataRepository = context.getBean(MetadataRepository.class);
        final int cnt = metadataRepository.sumOfPopularity(Optional.<Specification<Metadata>>absent());


		DefaultPieDataset dataset = new DefaultPieDataset();


		@SuppressWarnings("unchecked")

        "select sum(metadata.popularity) as popularity, metadatacateg.categoryid as categoryid, categories.name as categoryname from metadata, metadatacateg, categories where metadata.id = metadatacateg.metadataid and metadatacateg.categoryid = categories.id group by metadatacateg.categoryid, categories.name"
        List<Element> resultSet = dbms.select(query).getChildren();
		
		for (Element record : resultSet) {
			String popularity = record.getChildText("popularity");
			if (!popularity.equals("0")) {
				Double d = 0.0;
				if (popularity.length() > 0 ) {
					d = (Double.parseDouble(popularity) / cnt ) * 100; 
				}
				dataset.setValue(record.getChildText("categoryname"),d);
			}
		}
		
		// create a chart... 
		JFreeChart chart = ChartFactory.createPieChart( 
			null, 
			dataset, 
			true, // legend? 
			true, // tooltips? 
			false // URLs? 
		); 


		//hard coded values for the moment. should come from a configuration file.
		chart.setBackgroundPaint(Color.decode("#E7EDF5"));
		String chartFilename = "popularitybycategory_" + System.currentTimeMillis() + ".png";
		
		File statFolder = new File(gc.getBean(ServiceConfig.class).getMandatoryValue(
				Geonet.Config.RESOURCES_DIR) + File.separator + "images" + File.separator + "statTmp");
		IO.mkdirs(statFolder, "Statistices tmp dir");
		File f = new File(statFolder, chartFilename);
		this.imageMap = org.fao.geonet.services.statistics.ChartFactory.writeChartImage(
				chart, f, this.chartWidth, this.chartHeight, this.createTooltips, "graphPopularityByCategoryImageMap");
		// will return some info to the XSLT:
		// dateFrom, dateTo, graphicType, chartUrl, tooltipImageMap,
		// message, chartWidth, chartHeight
		
		Element elResp = new Element(Jeeves.Elem.RESPONSE);
		Element elchartUrl = new Element("popularityByCategoryUrl").setText(context.getBaseUrl() + 
				"/images/statTmp/" + chartFilename);		
		Element elTooltipImageMap = new Element("tooltipImageMap").addContent(
				this.createTooltips ? this.imageMap : "");
		
		Element elMessage = new Element("message").setText(message);		
		Element elChartWidth= new Element("chartWidth").setText("" + this.chartWidth);		
		Element elChartHeight= new Element("chartHeight").setText("" + this.chartHeight);	
		
		elResp.addContent(elchartUrl);
		elResp.addContent(elTooltipImageMap);
		elResp.addContent(elMessage);
		elResp.addContent(elChartWidth);
		elResp.addContent(elChartHeight);

		return elResp;
	}
}