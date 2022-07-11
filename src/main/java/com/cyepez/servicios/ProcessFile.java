package com.cyepez.servicios;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.MultipleBarcodeReader;
import com.sun.org.slf4j.internal.Logger;
import com.sun.org.slf4j.internal.LoggerFactory;

/**
 * Procesa archivo de tipo imágenes y pdf, buscando en estos códigos QR en los
 * que a su vez trata de encontrar una url que contenga un parámetro hasheado
 * en Base64. Este parámetro debe corresponder al formato de una factura AFIP.
 * @author Carlos Yépez.
 */
public class ProcessFile {
	
	private final static Logger log = LoggerFactory.getLogger(ProcessFile.class);

	/**
	 * Recibe el archivo stringBase64 y el tipo de archivo que es enviado al 
	 * endpoint a través del body.
	 * @param stringBase64
	 * @param tipoArchivo
	 * @return
	 * @throws Exception"error.formato.nosoportado"
	 */
	public static AFIPObject procesarArchivo(String stringBase64, 
			String tipoArchivo)
			throws Exception {
		
		if (stringBase64 == null || stringBase64.isEmpty() || 
				tipoArchivo == null || tipoArchivo.isEmpty()) {
			throw new Exception("error.datos.requeridos");
		}
		
		List<String> listStringFile = null;
		
		if (tipoArchivo.equalsIgnoreCase("image/jpeg") ||
				tipoArchivo.equalsIgnoreCase("image/jpg") ||
				tipoArchivo.equalsIgnoreCase("image/png")) {
			// No hago nada puesto la recomendación PAE es procesar imágenes.
			listStringFile = Arrays.asList(stringBase64);
		} else if (tipoArchivo.equalsIgnoreCase("application/pdf")) {
			// Convertir a imagen puesto la recomendación PAE es
			// trabajar con imágenes.
			listStringFile = pdfToImageStringBase64(stringBase64);
		} else {
			// Si el formato no corresponde a alguno de los presentes en la
			// validacíón, lanzo un exception.
			throw new Exception("error.formato.nosoportado");
		}
		
		AFIPObject afip = procesarImgB64(listStringFile);
		
		if (afip == null) {
			throw new Exception("error.afipvalido.noencontrado");
		}

		return afip;
		
	}
	
	/**
	 * Extrae informacion AFIP de una listado de imagenenes (En formato 
	 * StringBase64) que debe  contener al menos un código QR que apunte 
	 * a una URL la cual debe tener en un parámetro la información AFIP 
	 * mencionada, codificada en BASE64.
	 * @param listStringFile
	 * @return
	 * @throws Exception
	 */
	public static AFIPObject procesarImgB64(List<String> listStringFile) 
			throws Exception {
		
		AFIPObject afip = null;

		// Para cada archivo, extraigo y analizo cada QR.
		for (String stringFile: listStringFile) {
			
			try {
				byte[] bytes = DatatypeConverter.parseBase64Binary(stringFile);
				BufferedImage qrBufferedImg = ImageIO.read(new ByteArrayInputStream(bytes));
		        LuminanceSource source = 
		        		new BufferedImageLuminanceSource(qrBufferedImg);
		        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		        
		        MultipleBarcodeReader bcReader = 
		        		new GenericMultipleBarcodeReader(new MultiFormatReader());
		        
		        Hashtable<DecodeHintType, Object> filtros = new Hashtable<DecodeHintType, Object>();
		        filtros.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
		        filtros.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
		        
		        // Por cada qr prsente en cada archivo, verivico si posee info AFIP
		        for (Result result : bcReader.decodeMultiple(bitmap, filtros)) {
		        	
		        	Map<String, String> map = getURIQueryParamans(result.getText());
		        	
		        	try {
		        		if (map.get("p")!= null) {
			        		afip = new ObjectMapper().readValue(
			        				new String(Base64.getDecoder().decode(
			        						map.get("p"))), 
			        				AFIPObject.class);
			        	} else {
			        		afip = new ObjectMapper().readValue(
			        				new String(
			        						Base64.getDecoder().decode(
			        								result.getText())), 
			        				AFIPObject.class);
			        	}
		        		
		        		// Si el afip es válido, no recorro otros QR.
		        		if (validaAFIP(afip)) {
		        			break;
		        		}
		        		
		        		afip = null;
		        		
		        	} catch (Exception e) {
		        		afip = null;
		        	}
		        }
		        
		        // Si ya consiguió un AFIP válido, no recorro otros documentos.
		        if (afip != null) {
		        	break;
		        }
			} catch (Exception e) {
				log.error("No pude procesar el archivo IMAGEN stringBase64");
			}
	        
		}
		
        return afip;
	}
	
	/**
	 * Chequea que los campos AFIP estén conformes de acuerdo a las
	 * especifiaciones de los campos obligatorios.
	 * @param afip
	 * @return
	 */
	public static boolean validaAFIP(AFIPObject afip) {
		
		if (afip == null ||
				afip.getVer() == null || afip.getVer().isEmpty() ||
				afip.getFecha() == null || afip.getFecha().isEmpty() ||
				afip.getCuit() == null ||
				afip.getPtoVta() == null ||
				afip.getTipoCmp() == null ||
				afip.getNroCmp() == null ||
				afip.getImporte() == null ||
				afip.getMoneda() == null || afip.getMoneda().isEmpty() ||
				afip.getCtz() == null ||
				afip.getTipoCodAut() == null || afip.getTipoCodAut().isEmpty() ||
				afip.getCodAut() == null) {
			return false;
			
		}
		
		return true;
	}
	
	/**
	 * Dada una URL, retorna un mapa de parámetros presente en esta.
	 * @param url
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static Map<String, String> getURIQueryParamans(String url) 
			throws UnsupportedEncodingException{
		HashMap<String, String> map = new HashMap<String, String>();
		
		String query;
		try {
			query = new URL(url).getQuery();
			if (query == null || query.isEmpty()) {
				return map;
			}
			
			for (String param : query.split("&")) {
				String[] pair = param.split("=");
				String key = URLDecoder.decode(pair[0], "UTF-8");
				if (pair.length > 1) {
					map.put(key, URLDecoder.decode(pair[1], "UTF-8"));
				}
			}
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		return map;
	}
	
	
	/**
	 * Transforma un PDF en formato StringBase64 a tantas imagen StringBase64
	 * como paginas tenga el documento PDF.
	 * @param pdfBase64
	 * @return StringBase64
	 */
	public static List<String> pdfToImageStringBase64(String pdfBase64) {
		
		List<String> listImagString = new ArrayList<String>();
		PDDocument doc = null;

		try {
			byte[] pdfBytes = DatatypeConverter.parseBase64Binary(pdfBase64);
			doc = PDDocument.load(new ByteArrayInputStream(pdfBytes));
			PDFRenderer pdfRenderer = new PDFRenderer(doc);
			
			for (int p = 0; p < doc.getNumberOfPages(); ++p) {
				final ByteArrayOutputStream os = new ByteArrayOutputStream();
				BufferedImage image = null;
				
				try {
					image = pdfRenderer.renderImageWithDPI(p, 300, ImageType.RGB);
					ImageIO.write(image, "jpg", os);
					listImagString.add(
							Base64.getEncoder().encodeToString(os.toByteArray()));
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					os.close();
				}
			}
			doc.close();
			
		} catch (Exception e) {
			log.error("No pude procesar el PDF en formato Base64");
			e.printStackTrace();
		} 
		
		return listImagString;
	}
	
	
	/**
	 * Clase asociada a la estructura AFIP indicada por PAE.
	 * @author Carlos Yepez.
	 */
	public static class AFIPObject implements Serializable {

		private static final long serialVersionUID = 1L;
		
		private String ver;
		
		// formato YYYY-mm-dd
		private String fecha;
		
		private Long cuit;
		
		private Integer ptoVta;

		private Integer tipoCmp;
		
		private Integer nroCmp;

		private Double importe;
		
		private String moneda;
		
		private Float ctz;
		
		private Integer tipoDocRec;
		
		private Long nroDocRec;
		
		private String tipoCodAut;
		
		private Long codAut;

		/**
		 * Constructor por defecto
		 */
		public AFIPObject() {
			super();
		}

		/**
		 * Constructor con todos los parámetros.
		 * @param ver
		 * @param fecha
		 * @param cuit
		 * @param ptoVta
		 * @param tipoCmp
		 * @param nroCmp
		 * @param importe
		 * @param moneda
		 * @param ctz
		 * @param tipoDocRec
		 * @param nroDocRec
		 * @param tipoCodAut
		 * @param codAut
		 */
		public AFIPObject(String ver, String fecha, Long cuit, Integer ptoVta,
				Integer tipoCmp, Integer nroCmp, Double importe, String moneda,
				Float ctz, Integer tipoDocRec, Long nroDocRec, 
				String tipoCodAut, Long codAut) {
			super();
			this.ver = ver;
			this.fecha = fecha;
			this.cuit = cuit;
			this.ptoVta = ptoVta;
			this.tipoCmp = tipoCmp;
			this.nroCmp = nroCmp;
			this.importe = importe;
			this.moneda = moneda;
			this.ctz = ctz;
			this.tipoDocRec = tipoDocRec;
			this.nroDocRec = nroDocRec;
			this.tipoCodAut = tipoCodAut;
			this.codAut = codAut;
		}

		// Getters y Setters
		
		public String getVer() {
			return ver;
		}

		public void setVer(String ver) {
			this.ver = ver;
		}

		public String getFecha() {
			return fecha;
		}

		public void setFecha(String fecha) {
			this.fecha = fecha;
		}

		public Long getCuit() {
			return cuit;
		}

		public void setCuit(Long cuit) {
			this.cuit = cuit;
		}

		public Integer getPtoVta() {
			return ptoVta;
		}

		public void setPtoVta(Integer ptoVta) {
			this.ptoVta = ptoVta;
		}

		public Integer getTipoCmp() {
			return tipoCmp;
		}

		public void setTipoCmp(Integer tipoCmp) {
			this.tipoCmp = tipoCmp;
		}

		public Integer getNroCmp() {
			return nroCmp;
		}

		public void setNroCmp(Integer nroCmp) {
			this.nroCmp = nroCmp;
		}

		public Double getImporte() {
			return importe;
		}

		public void setImporte(Double importe) {
			this.importe = importe;
		}

		public String getMoneda() {
			return moneda;
		}

		public void setMoneda(String moneda) {
			this.moneda = moneda;
		}

		public Float getCtz() {
			return ctz;
		}

		public void setCtz(Float ctz) {
			this.ctz = ctz;
		}

		public Integer getTipoDocRec() {
			return tipoDocRec;
		}

		public void setTipoDocRec(Integer tipoDocRec) {
			this.tipoDocRec = tipoDocRec;
		}

		public Long getNroDocRec() {
			return nroDocRec;
		}

		public void setNroDocRec(Long nroDocRec) {
			this.nroDocRec = nroDocRec;
		}

		public String getTipoCodAut() {
			return tipoCodAut;
		}

		public void setTipoCodAut(String tipoCodAut) {
			this.tipoCodAut = tipoCodAut;
		}

		public Long getCodAut() {
			return codAut;
		}

		public void setCodAut(Long codAut) {
			this.codAut = codAut;
		}
		
	}

}
