# SOURCE CODE EXTRACTION — 5 Components

---

## ITEM 1: MbsBrimChargeService (MBS API)

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/brim/Service/MbsBrimChargeService.java`

```java
package com.lumen.mbs.brim.Service;

import java.util.List;

import com.lumen.mbs.brim.dto.MbsBrimChargeRequest;
import com.lumen.mbs.brim.dto.MbsBrimChargeResponse;

public interface MbsBrimChargeService {

    List<MbsBrimChargeResponse> calculateCharges(MbsBrimChargeRequest request, boolean isTaxPresent) throws Exception;
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/brim/Service/MbsBrimChargeServiceImpl.java`

```java
package com.lumen.mbs.brim.Service;

import com.lumen.mbs.api.service.CostCenterMappingService;
import com.lumen.mbs.brim.dto.BartLpxProduct;
import com.lumen.mbs.brim.dto.MbsBrimChargeRequest;
import com.lumen.mbs.brim.dto.MbsBrimChargeResponse;
import com.lumen.mbs.brim.dto.MbsBrimUsageQueryResultDTO;
import com.lumen.mbs.customerBilling.dto.CostCenterMappingResponse;
import com.lumen.mbs.exception.CostCenterIdNotFoundException;
import com.lumen.mbs.model.MBSBillCompute;
import com.lumen.mbs.repository.BrimComputeRepository;
import com.lumen.mbs.repository.MBSBillComputeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * jira-id : CPPEWMB-5443
 * code changes by AD41939 - Abubaker
 * date : 02-05-2024
 * desc: Included a method to handle the legacy product code for LDI and LDS
 */

@Service
public class MbsBrimChargeServiceImpl implements MbsBrimChargeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MbsBrimChargeServiceImpl.class);
    private static final String INTERSTATE = "INTERSTATE";
    private static final String INTRASTATE = "INTRASTATE";
    private static final String RAC_XREF = "RAC-XREF";

    //jira-id : CPPEWMB-5443-start
    private static final String CAT11_LD = "CAT11 LD";
    private static final String B_AND_C_ALL_LD = "B&C/ALL/LD";
    private static final String CLECSWA_ALL_LD = "CLECSWA/ALL/LD";
    //jira-id : CPPEWMB-5443-end


    @Autowired
    private MBSBillComputeRepository mbsBillComputeRepository;
    
    @Autowired
	private CostCenterMappingService costCenterMappingService;


    @Autowired
    private MbsBrimHelper mbsBrimHelper;

    //jira-id: CPPEWMB-6086-start
    @Autowired
    private BrimComputeRepository brimComputeRepository;
    //jira-id: CPPEWMB-6086-end

    @Override
    public List<MbsBrimChargeResponse> calculateCharges(MbsBrimChargeRequest request, boolean isTaxPresent) {
        String crgCustomerId = request.getCrgCustomerId().toUpperCase();
        String crgInvoiceNumber = request.getCrgInvoiceNumber().toUpperCase();
        String crgSystemName = request.getCrgSystemName().toUpperCase();
        String crgBillDate = request.getCrgBillDate();

        Date billdate = parseDate(crgBillDate);

        List<Object[]> customQueryResults;
        List<MbsBrimUsageQueryResultDTO> mbsBrimUsageQueryResultDTOList;
        List<MBSBillCompute> mbsBillComputesFromCustomQuery;

        customQueryResults = brimComputeRepository.billComputeNativeQuery(crgCustomerId, crgBillDate, crgInvoiceNumber,crgSystemName);
        mbsBrimUsageQueryResultDTOList = mapQueryResultToDtoWithTax(customQueryResults);
        
        mbsBillComputesFromCustomQuery = mapQueryResultDtoToBillCompute(mbsBrimUsageQueryResultDTOList, billdate, isTaxPresent);

        List<MbsBrimChargeResponse> brimChargeResponseList = new ArrayList<>();
      
        if (!mbsBillComputesFromCustomQuery.isEmpty()) {
            for (MBSBillCompute billCompute : mbsBillComputesFromCustomQuery) {
                try {
                	 boolean processed =  processBillCompute(billCompute, request, brimChargeResponseList);
                	 if (!processed) {
                         LOGGER.error("Cost center validation failed for customerId: {} and invoiceNumber: {}. Returning null.",
                                 request.getCrgCustomerId(), request.getCrgInvoiceNumber());
                         return null;
                     }
                } catch (Exception e) {
                    LOGGER.error("Error while calculating charges for customerId: {} and invoiceNumber: {}, Exception: {}",
                            request.getCrgCustomerId(), request.getCrgInvoiceNumber(), e.getMessage());
                    e.printStackTrace();
                }
            }
        } else {
            LOGGER.info("No data found in MBSBillCompute for customerId: {} and invoiceNumber: {}",
                    request.getCrgCustomerId(), request.getCrgInvoiceNumber());
        }

        return brimChargeResponseList;
    }

    private Date parseDate(String crgBillDate) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy");
        try {
            return dateFormat.parse(crgBillDate);
        } catch (ParseException e) {
            LOGGER.error("Error while parsing date: {}, Exception: {}", crgBillDate, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private boolean processBillCompute(MBSBillCompute billCompute, MbsBrimChargeRequest request, List<MbsBrimChargeResponse> brimChargeResponseList) {
        if (((!(billCompute.getBillcdInterSum().compareTo(BigDecimal.ZERO) == 0)) && (!(billCompute.getBillServCdInterRate().compareTo(BigDecimal.ZERO) == 00)))
                || (!(billCompute.getTotBillcdInterAmt().compareTo(BigDecimal.ZERO) == 00))) {

            MbsBrimChargeResponse mbsBrimChargeResponse = calculateChargeForBillCompute(billCompute, INTERSTATE,
                    request.getCrgAffiliateIndr().trim(), request.getCrgEntpId().trim());

            if (mbsBrimChargeResponse == null) {
            	return false;
            }
            brimChargeResponseList.add(mbsBrimChargeResponse);
        }

        if (((!(billCompute.getBillcdIntraSum().compareTo(BigDecimal.ZERO) == 0)) && (!(billCompute.getBillServCdIntraRate().compareTo(BigDecimal.ZERO) == 00)))
                || (!(billCompute.getTotBillcdIntraAmt().compareTo(BigDecimal.ZERO) == 00))) {

            MbsBrimChargeResponse mbsBrimChargeResponse = calculateChargeForBillCompute(billCompute, INTRASTATE,
                    request.getCrgAffiliateIndr().trim(), request.getCrgEntpId().trim());

            if (mbsBrimChargeResponse == null) {
            	return false;
            }
            brimChargeResponseList.add(mbsBrimChargeResponse);
        }
        
        return true;
    }


    private MbsBrimChargeResponse calculateChargeForBillCompute(MBSBillCompute billCompute, String interOrIntra,
                                                                String crgAffiliateIndr, String entpId) {
        String prodServRac = billCompute.getProdServRac();
        String mCustomerId = billCompute.getCustomerId().trim();
        String mSystemName = billCompute.getSystemName().trim();
        String costCenter = billCompute.getCostCenter();

        String productType = "";
        String mbsProductCode = "";
        BartLpxProduct resultFromLPXProd = new BartLpxProduct();

        String wbsCd = mbsBrimHelper.getwbCd(billCompute);
        String legacyProdCd = billCompute.getLegacyProductCode();
        String castFieldString = billCompute.getCastField();

        String legacyProdCdBeforeProcessing = legacyProdCd.trim();

        legacyProdCd = mbsBrimHelper.processLegacyProdCd(legacyProdCd, interOrIntra);

        resultFromLPXProd = mbsBrimHelper.getProductServiceRACAndCostCenter(legacyProdCdBeforeProcessing, interOrIntra,
                crgAffiliateIndr, mSystemName, billCompute.getCastField());

        //Product Service RAC / GL RAC
        if (prodServRac == null || prodServRac.trim().isEmpty() || prodServRac.trim().equals(RAC_XREF)) {
            if (resultFromLPXProd != null && resultFromLPXProd.getGlRac() != null && !resultFromLPXProd.getGlRac().trim().isEmpty()) {
                prodServRac = resultFromLPXProd.getGlRac().trim();
            } else {
                prodServRac = "";
            }
        }

        //Cost Center
        if (costCenter == null || costCenter.trim().isEmpty()) {
            if (resultFromLPXProd != null && resultFromLPXProd.getGlRac() != null && resultFromLPXProd.getGlRac().startsWith("6")) {
                if (resultFromLPXProd.getCostCenter() != null && !resultFromLPXProd.getCostCenter().trim().isEmpty()) {
                    costCenter = (entpId.substring(0, 2).trim() + mCustomerId.substring(0, 2).trim()) +
                            resultFromLPXProd.getCostCenter();
                } else {
                    costCenter = "";
                }
            } else {
                costCenter = "";
            }
        }
        
        if(costCenter != null && !costCenter.isEmpty() ) {
        	 try {
				    CostCenterMappingResponse response =
				            costCenterMappingService.getCostCenterMappingResponse(costCenter, entpId);
				    String s4CostCenter = response.getS4CostCenter();
				    if (!costCenter.equals(s4CostCenter)) {
				    	LOGGER.info("Updating legacy cost center [{}] to S4 cost center [{}]", costCenter, s4CostCenter);
				    	costCenter=s4CostCenter;
				    }
				} catch (CostCenterIdNotFoundException ex) {
					LOGGER.error("Cost center mapping failed for [{}]", costCenter, ex);
				    return null;
				}
        }

        //findProductCodeAndType
        if (resultFromLPXProd != null && resultFromLPXProd.getMbsProductCode() != null && resultFromLPXProd.getProductType() != null) {
            if (!mSystemName.equals("LEXCIS") && (castFieldString == null || castFieldString.trim().isEmpty())) {
                mbsProductCode += legacyProdCdBeforeProcessing;
                productType += "OBC";
            } else if (castFieldString != null && !castFieldString.trim().isEmpty()) {
                String[] castField = castFieldString.split("\\|");
                if (castField.length > 3) {
                    mbsProductCode = resultFromLPXProd.getMbsProductCode().trim();
                    productType = resultFromLPXProd.getProductType().trim();
                } else {
                    LOGGER.info("No mbsProductCode and productType set, Invalid or insufficient data in castField.");
                }
            } else {
                LOGGER.info("castFieldString is null or empty.");
            }
        } else {
            mbsProductCode = "";
            productType = "";
        }

        return createBrimChargeResponse(billCompute, interOrIntra, costCenter, prodServRac, wbsCd, legacyProdCd,
                productType, mbsProductCode, legacyProdCdBeforeProcessing);
    }


    private MbsBrimChargeResponse createBrimChargeResponse(MBSBillCompute billCompute, String interOrIntra,
                                                           String updatedCostCenter, String prodServRac, String wbsCd,
                                                           String legacyProdCd, String productType, String mbsProductCode,
                                                           String legacyProdCdBeforeProcessing) {

        MbsBrimChargeResponse mbsBrimChargeResponse = new MbsBrimChargeResponse();

        mbsBrimChargeResponse.setCrgCustomerNumber(billCompute.getCustomerId() != null ? billCompute.getCustomerId().trim() : "");
        mbsBrimChargeResponse.setCrgInvoiceNumber(billCompute.getInvoiceNum() != null ? billCompute.getInvoiceNum().trim() : "");
        mbsBrimChargeResponse.setCrgJurisdiction(interOrIntra != null ? interOrIntra.trim() : "");
        mbsBrimChargeResponse.setCrgSeqNo(billCompute.gettComp1SeqNum());
        mbsBrimChargeResponse.setCrgBillServCd(billCompute.getBillServCd() != null ? billCompute.getBillServCd().trim() : "");

        if (interOrIntra != null && interOrIntra.equals(INTERSTATE)) {
            mbsBrimChargeResponse.setCrgBillServInterRate(billCompute.getBillServCdInterRate());
            mbsBrimChargeResponse.setCrgBillcdInterSum(billCompute.getBillcdInterSum());
            mbsBrimChargeResponse.setCrgTotBillcdInterAmt(billCompute.getTotBillcdInterAmt());
        }

        if (interOrIntra != null && interOrIntra.equals(INTRASTATE)) {
            mbsBrimChargeResponse.setCrgBillServIntraRate(billCompute.getBillServCdIntraRate());
            mbsBrimChargeResponse.setCrgBillcdIntraSum(billCompute.getBillcdIntraSum());
            mbsBrimChargeResponse.setCrgTotBillcdIntraAmt(billCompute.getTotBillcdIntraAmt());
        }

        mbsBrimChargeResponse.setCrgLongDescription(billCompute.getLongDescription() != null ?
                billCompute.getLongDescription().trim() : "");
        mbsBrimChargeResponse.setCrgProdServRac(prodServRac);
        mbsBrimChargeResponse.setCrgProdServAddr(billCompute.getProdServAddr());
        mbsBrimChargeResponse.setCrgProdServZip(billCompute.getProdServZip());

        if(billCompute.getProdServState()!=null && !billCompute.getProdServState().trim().isEmpty()) {
       	    mbsBrimChargeResponse.setCrgProdServState(billCompute.getProdServState().trim());
        } else {
       	    mbsBrimChargeResponse.setCrgProdServState(billCompute.getStateCd().trim());
        }

        mbsBrimChargeResponse.setCrgCostCenter(updatedCostCenter);
        mbsBrimChargeResponse.setCrgWbsCd(wbsCd != null ? wbsCd.trim() : "");
        mbsBrimChargeResponse.setCrgChargeSrc(billCompute.getChargeSrc() != null ? billCompute.getChargeSrc().trim() : "");
        mbsBrimChargeResponse.setCrgLegacyProdCd(legacyProdCd != null ? legacyProdCd.trim() : "");

        if (legacyProdCdBeforeProcessing.equals(mbsProductCode)) {
            mbsBrimChargeResponse.setCrgSourceType(productType);
        } else {
            mbsBrimChargeResponse.setCrgSourceType("OBC");
        }
        mbsBrimChargeResponse.setCrgSystemName(billCompute.getSystemName() != null ? billCompute.getSystemName().trim() : "");

        return mbsBrimChargeResponse;
    }


    private List<MBSBillCompute> mapQueryResultDtoToBillCompute(List<MbsBrimUsageQueryResultDTO>
                                                                        mbsBrimUsageQueryResultDTOList, Date billDate,
                                                                boolean isTaxPresent) {
        List<MBSBillCompute> mbsBillComputes = new ArrayList<>();
        for (MbsBrimUsageQueryResultDTO mbsBrimUsageQueryResultDTO : mbsBrimUsageQueryResultDTOList) {
            MBSBillCompute mbsBillCompute = new MBSBillCompute();

            mbsBillCompute.setInvoiceNum(mbsBrimUsageQueryResultDTO.getInvoiceNum().trim());
            mbsBillCompute.setBillDate(billDate);
            mbsBillCompute.setProdServRac(mbsBrimUsageQueryResultDTO.getProdServRac().trim());
            mbsBillCompute.setProdServRcc(mbsBrimUsageQueryResultDTO.getProdServRcc().trim());
            mbsBillCompute.setCustomerId(mbsBrimUsageQueryResultDTO.getCustomerId().trim());
            mbsBillCompute.setStateCd(mbsBrimUsageQueryResultDTO.getStateCd().trim());
            mbsBillCompute.setLongDescription(mbsBrimUsageQueryResultDTO.getLongDescription().trim());
            mbsBillCompute.setBillcdInterSum(mbsBrimUsageQueryResultDTO.getBilcdInterSum());
            mbsBillCompute.setTotBillcdInterAmt(mbsBrimUsageQueryResultDTO.getTotBilcdInterAmt().setScale(2, RoundingMode.HALF_UP));
            mbsBillCompute.setBillServCdInterRate(mbsBrimUsageQueryResultDTO.getBillServCdInterRate());
            mbsBillCompute.setWbsCd(mbsBrimUsageQueryResultDTO.getWbsCd().trim());
            mbsBillCompute.setCostCenter(mbsBrimUsageQueryResultDTO.getCostCenter().trim());
            mbsBillCompute.setExpenditureTypeCode(mbsBrimUsageQueryResultDTO.getExpenditureTypeCode().trim());
            mbsBillCompute.setSystemName(mbsBrimUsageQueryResultDTO.getSystemName().trim());
            mbsBillCompute.setBillcdIntraSum(mbsBrimUsageQueryResultDTO.getBilcdIntraSum());
            mbsBillCompute.setTotBillcdIntraAmt((mbsBrimUsageQueryResultDTO.getTotBilcdIntraAmt()).setScale(2, RoundingMode.HALF_UP));
            mbsBillCompute.setBillServCdIntraRate(mbsBrimUsageQueryResultDTO.getBillServCdIntraRate());
            mbsBillCompute.setBillServCd(mbsBrimUsageQueryResultDTO.getBillServCd().trim());
            mbsBillCompute.setProdServAddr(mbsBrimUsageQueryResultDTO.getProdServAddr());
            mbsBillCompute.setProdServZip(mbsBrimUsageQueryResultDTO.getProdServZip());
            mbsBillCompute.setProdServState(mbsBrimUsageQueryResultDTO.getProdServState());
            mbsBillCompute.setChargeSrc(mbsBrimUsageQueryResultDTO.getChargeSrc().trim());
            mbsBillCompute.setLegacyProductCode(mbsBrimUsageQueryResultDTO.getLegacyProductCd());
            mbsBillCompute.setCastField(mbsBrimUsageQueryResultDTO.getCastField());

            mbsBillComputes.add(mbsBillCompute);
        }
        return mbsBillComputes;
    }


	private List<MbsBrimUsageQueryResultDTO> mapQueryResultToDtoWithTax(List<Object[]> customQueryResults) {
        List<MbsBrimUsageQueryResultDTO> mbsBrimUsageQueryResultDTOList = new ArrayList<>();

        for (Object[] customQueryResult : customQueryResults) {
            MbsBrimUsageQueryResultDTO mbsBrimUsageQueryResultDTO = new MbsBrimUsageQueryResultDTO();

            mbsBrimUsageQueryResultDTO.setEntpId(customQueryResult[0] != null ? customQueryResult[0].toString() : "");
            mbsBrimUsageQueryResultDTO.setProdServRac(customQueryResult[1] != null ? customQueryResult[1].toString() : "");
            mbsBrimUsageQueryResultDTO.setProdServRcc(customQueryResult[2] != null ? customQueryResult[2].toString() : "");
            mbsBrimUsageQueryResultDTO.setCustomerId(customQueryResult[3] != null ? customQueryResult[3].toString() : "");
            mbsBrimUsageQueryResultDTO.setStateCd(customQueryResult[4] != null ? customQueryResult[4].toString() : "");
            mbsBrimUsageQueryResultDTO.setInvoiceNum(customQueryResult[5] != null ? customQueryResult[5].toString() : "");
            mbsBrimUsageQueryResultDTO.setLegacyProductCd(customQueryResult[6] != null ? customQueryResult[6].toString() : "");
            mbsBrimUsageQueryResultDTO.setBilcdInterSum(customQueryResult[7] != null ?
                    new BigDecimal(String.valueOf(customQueryResult[7])) : BigDecimal.ZERO);
            mbsBrimUsageQueryResultDTO.setTotBilcdInterAmt(customQueryResult[8] != null ?
                    new BigDecimal(String.valueOf(customQueryResult[8])) : BigDecimal.ZERO);
            mbsBrimUsageQueryResultDTO.setWbsCd(customQueryResult[9] != null ? customQueryResult[9].toString() : "");
            mbsBrimUsageQueryResultDTO.setCostCenter(customQueryResult[10] != null ? customQueryResult[10].toString() : "");
            mbsBrimUsageQueryResultDTO.setExpenditureTypeCode(customQueryResult[11] != null ? customQueryResult[11].toString() : "");
            mbsBrimUsageQueryResultDTO.setPaymentSeqNum(customQueryResult[12] != null ? customQueryResult[12].toString() : "");
            mbsBrimUsageQueryResultDTO.setSystemName(customQueryResult[13] != null ? customQueryResult[13].toString() : "");
            mbsBrimUsageQueryResultDTO.setBilcdIntraSum(customQueryResult[14] != null ?
                    new BigDecimal(String.valueOf(customQueryResult[14])) : BigDecimal.ZERO);
            mbsBrimUsageQueryResultDTO.setTotBilcdIntraAmt(customQueryResult[15] != null ?
                    new BigDecimal(String.valueOf(customQueryResult[15])) : BigDecimal.ZERO);
            mbsBrimUsageQueryResultDTO.setCastField(customQueryResult[16] != null ? customQueryResult[16].toString() : "");
            mbsBrimUsageQueryResultDTO.setBillServCd(customQueryResult[17] != null ? customQueryResult[17].toString() : "");
            mbsBrimUsageQueryResultDTO.setProdServAddr(customQueryResult[18] != null ? customQueryResult[18].toString() : null);
            mbsBrimUsageQueryResultDTO.setProdServZip(customQueryResult[19] != null ? customQueryResult[19].toString() : null);
            mbsBrimUsageQueryResultDTO.setProdServState(customQueryResult[20] != null ? customQueryResult[20].toString() : "");
            mbsBrimUsageQueryResultDTO.setChargeSrc(customQueryResult[21] != null ? customQueryResult[21].toString() : "");
            mbsBrimUsageQueryResultDTO.setLongDescription(customQueryResult[22] != null ? customQueryResult[22].toString() : "");
            mbsBrimUsageQueryResultDTO.setBillServCdInterRate(customQueryResult[23] != null ?
                    new BigDecimal(String.valueOf(customQueryResult[23])) : BigDecimal.ZERO);
            mbsBrimUsageQueryResultDTO.setBillServCdIntraRate(customQueryResult[24] != null ?
                    new BigDecimal(String.valueOf(customQueryResult[24])) : BigDecimal.ZERO);

            mbsBrimUsageQueryResultDTOList.add(mbsBrimUsageQueryResultDTO);
        }
        return mbsBrimUsageQueryResultDTOList;
    }

    private List<MbsBrimUsageQueryResultDTO> mapQueryResultToDtoWithoutTaxes(List<Object[]> customQueryResults) {
        List<MbsBrimUsageQueryResultDTO> mbsBrimUsageQueryResultDTOList = new ArrayList<>();

        for (Object[] customQueryResult : customQueryResults) {
            MbsBrimUsageQueryResultDTO mbsBrimUsageQueryResultDTO = new MbsBrimUsageQueryResultDTO();

            mbsBrimUsageQueryResultDTO.setEntpId(customQueryResult[0] != null ? customQueryResult[0].toString() : "");
            mbsBrimUsageQueryResultDTO.setProdServRac(customQueryResult[1] != null ? customQueryResult[1].toString() : "");
            mbsBrimUsageQueryResultDTO.setProdServRcc(customQueryResult[2] != null ? customQueryResult[2].toString() : "");
            mbsBrimUsageQueryResultDTO.setCustomerId(customQueryResult[3] != null ? customQueryResult[3].toString() : "");
            mbsBrimUsageQueryResultDTO.setStateCd(customQueryResult[4] != null ? customQueryResult[4].toString() : "");
            mbsBrimUsageQueryResultDTO.setInvoiceNum(customQueryResult[5] != null ? customQueryResult[5].toString() : "");
            mbsBrimUsageQueryResultDTO.setLegacyProductCd(customQueryResult[6] != null ? customQueryResult[6].toString() : "");
            mbsBrimUsageQueryResultDTO.setBilcdInterSum(customQueryResult[7] != null ? new BigDecimal(String.valueOf(customQueryResult[7])) : BigDecimal.ZERO);
            mbsBrimUsageQueryResultDTO.setTotBilcdInterAmt(customQueryResult[8] != null ? new BigDecimal(String.valueOf(customQueryResult[8])) : BigDecimal.ZERO);
            mbsBrimUsageQueryResultDTO.setWbsCd(customQueryResult[9] != null ? customQueryResult[9].toString() : "");
            mbsBrimUsageQueryResultDTO.setCostCenter(customQueryResult[10] != null ? customQueryResult[10].toString() : "");
            mbsBrimUsageQueryResultDTO.setExpenditureTypeCode(customQueryResult[11] != null ? customQueryResult[11].toString() : "");
            mbsBrimUsageQueryResultDTO.setPaymentSeqNum(customQueryResult[12] != null ? customQueryResult[12].toString() : "");
            mbsBrimUsageQueryResultDTO.setSystemName(customQueryResult[13] != null ? customQueryResult[13].toString() : "");
            mbsBrimUsageQueryResultDTO.setBilcdIntraSum(customQueryResult[14] != null ?
                    new BigDecimal(String.valueOf(customQueryResult[14])) : BigDecimal.ZERO);
            mbsBrimUsageQueryResultDTO.setTotBilcdIntraAmt(customQueryResult[15] != null ?
                    new BigDecimal(String.valueOf(customQueryResult[15])) : BigDecimal.ZERO);
            mbsBrimUsageQueryResultDTO.setCastField(customQueryResult[16] != null ? customQueryResult[16].toString() : "");
            mbsBrimUsageQueryResultDTO.setBillServCd(customQueryResult[17] != null ? customQueryResult[17].toString() : "");
            mbsBrimUsageQueryResultDTO.setProdServState(customQueryResult[18] != null ? customQueryResult[18].toString() : "");
            mbsBrimUsageQueryResultDTO.setChargeSrc(customQueryResult[19] != null ? customQueryResult[19].toString() : "");
            mbsBrimUsageQueryResultDTO.setLongDescription(customQueryResult[20] != null ? customQueryResult[20].toString() : "");
            mbsBrimUsageQueryResultDTO.setBillServCdInterRate(customQueryResult[21] != null ?
                    new BigDecimal(String.valueOf(customQueryResult[21])) : BigDecimal.ZERO);
            mbsBrimUsageQueryResultDTO.setBillServCdIntraRate(customQueryResult[22] != null ?
                    new BigDecimal(String.valueOf(customQueryResult[22])) : BigDecimal.ZERO);

            mbsBrimUsageQueryResultDTOList.add(mbsBrimUsageQueryResultDTO);
        }
        return mbsBrimUsageQueryResultDTOList;
    }
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/brim/dto/MbsBrimChargeRequest.java`

```java
package com.lumen.mbs.brim.dto;

public class MbsBrimChargeRequest {

    private String crgCustomerId;
    private String crgInvoiceNumber;
    private String crgBillDate;
    private String crgAffiliateIndr;
    private String crgEntpId;
    private String crgSystemName;

    public String getCrgCustomerId() { return crgCustomerId; }
    public void setCrgCustomerId(String crgCustomerId) { this.crgCustomerId = crgCustomerId; }
    public String getCrgInvoiceNumber() { return crgInvoiceNumber; }
    public void setCrgInvoiceNumber(String crgInvoiceNumber) { this.crgInvoiceNumber = crgInvoiceNumber; }
    public String getCrgBillDate() { return crgBillDate; }
    public void setCrgBillDate(String crgBillDate) { this.crgBillDate = crgBillDate; }
    public String getCrgAffiliateIndr() { return crgAffiliateIndr; }
    public void setCrgAffiliateIndr(String crgAffiliateIndr) { this.crgAffiliateIndr = crgAffiliateIndr; }
    public String getCrgEntpId() { return crgEntpId; }
    public void setCrgEntpId(String crgEntpId) { this.crgEntpId = crgEntpId; }
    public String getCrgSystemName() { return crgSystemName; }
    public void setCrgSystemName(String crgSystemName) { this.crgSystemName = crgSystemName; }
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/brim/dto/MbsBrimChargeResponse.java`

```java
package com.lumen.mbs.brim.dto;

import java.math.BigDecimal;

public class MbsBrimChargeResponse {

    private String crgCustomerNumber;
    private String crgInvoiceNumber;
    private String crgJurisdiction;
    private long crgSeqNo;
    private String crgBillServCd;
    private BigDecimal crgBillServInterRate;
    private BigDecimal crgBillcdInterSum;
    private BigDecimal crgTotBillcdInterAmt;
    private BigDecimal crgBillServIntraRate;
    private BigDecimal crgBillcdIntraSum;
    private BigDecimal crgTotBillcdIntraAmt;
    private String crgLongDescription;
    private String crgProdServRac;
    private String crgProdServAddr;
    private String crgProdServZip;
    private String crgProdServState;
    private String crgCostCenter;
    private String crgWbsCd;
    private String crgChargeSrc;
    private String crgLegacyProdCd;
    private String crgSourceType;
    private String crgSystemName;

    public MbsBrimChargeResponse(String crgCustomerNumber, String crgInvoiceNumber, String crgJurisdiction,
    		long crgSeqNo, String crgBillServCd, BigDecimal crgBillServInterRate,
            BigDecimal crgBillcdInterSum, BigDecimal crgTotBillcdInterAmt,
            BigDecimal crgBillServIntraRate, BigDecimal crgBillcdIntraSum,
            BigDecimal crgTotBillcdIntraAmt, String crgLongDescription, String crgProdServRac,
            String crgProdServAddr, String crgProdServZip, String crgProdServState,
            String crgCostCenter, String crgWbsCd, String crgChargeSrc, String crgLegacyProdCd,
            String crgSystemName, String crgSourceType) {
        // ... all field assignments ...
    }

    public MbsBrimChargeResponse() {}

    // ... all getters and setters for every field ...
}
```

---

### Controller methods in `mbs-app-API/src/main/java/com/lumen/mbs/controller/MbsBrimFileController.java` that invoke charge service:

The charge service is called indirectly via `MbsBrimService.getBrimData()` (called from `/MbsBrimFileDetails`). The controller does not directly invoke `MbsBrimChargeService` — it is called by `MbsBrimService` during BRIM file processing.

```java
@PostMapping(value = "/MbsBrimFileDetails", produces = MediaType.APPLICATION_JSON_VALUE)
public MbsBrimResponse MbsBrimFileDetails(@RequestBody MbsBrimRequest mbsBrimRequest) {
    MbsBrimResponse mbsBrimResponse= new MbsBrimResponse();
    mbsBrimResponse= mbsBrimService.getBrimData(mbsBrimRequest,mbsBrimResponse);
    return mbsBrimResponse;
}
```

---

### Dependency: `mbs-app-API/src/main/java/com/lumen/mbs/repository/BrimComputeRepository.java`

```java
package com.lumen.mbs.repository;

import com.lumen.mbs.model.MbsBrimUsageQueryResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrimComputeRepository extends JpaRepository<MbsBrimUsageQueryResult, Long> {

    @Query(value = "select distinct cd.entp_id, bcp.PROD_SERV_RAC, bcp.PROD_SERV_RCC, bcp.customer_id, bcp.state_cd, " +
            "bcp.invoice_num, bcp.LEGACY_PRODUCT_CD, bcp.bilcd_inter_sum, bcp.tot_bilcd_inter_amt, " +
            "bcp.WBS_CD, bcp.COST_CENTER, bcp.EXPENDITURE_TYPE_CODE, ' ' PAYMENT_SEQ_NUM, bcp.SYSTEM_NAME, " +
            "bcp.bilcd_intra_sum, bcp.tot_bilcd_intra_amt, bcp.CAST_FIELD, bcp.BILL_SERV_CD, " +
            "bcp.PROD_SERV_ADDR, bcp.PROD_SERV_ZIP, bcp.PROD_SERV_STATE, bcp.CHARGE_SRC, bcp.LONG_DESCRIPTION, " +
            "bcp.BILL_SERV_CD_INTER_RATE, bcp.BILL_SERV_CD_INTRA_RATE from " +
            "( select PROD_SERV_RAC, PROD_SERV_RCC, customer_id, state_cd, invoice_num, LEGACY_PRODUCT_CD, " +
            " EXPENDITURE_TYPE_CODE, COST_CENTER, WBS_CD, sum(bilcd_inter_sum) bilcd_inter_sum, " +
            " sum(tot_bilcd_inter_amt) tot_bilcd_inter_amt, SYSTEM_NAME, sum(bilcd_intra_sum) bilcd_intra_sum, " +
            " sum(tot_bilcd_intra_amt) tot_bilcd_intra_amt, CAST_FIELD, BILL_SERV_CD, PROD_SERV_ADDR, " +
            " PROD_SERV_ZIP, PROD_SERV_STATE, CHARGE_SRC, LONG_DESCRIPTION, BILL_SERV_CD_INTER_RATE, " +
            " BILL_SERV_CD_INTRA_RATE from " +
            " ( select bc.PROD_SERV_RAC, bc.PROD_SERV_RCC, bc.customer_id, bc.WBS_CD, bc.state_cd, bc.invoice_num, " +
            " ps.LEGACY_PRODUCT_CD, bc.bilcd_inter_sum, " +
            " (bc.bill_Serv_cd_inter_rate * bc.BILCD_INTER_SUM) as tot_bilcd_inter_amt, " +
            " COST_CENTER as COST_CENTER, NVL(EXPENDITURE_TYPE_CODE, ' ') as EXPENDITURE_TYPE_CODE, " +
            " bc.system_name, bc.bilcd_intra_sum, " +
            " (bc.bill_Serv_cd_intra_rate * bc.BILCD_INTRA_SUM) as tot_bilcd_intra_amt, " +
            " ps.CAST_FIELD, bc.BILL_SERV_CD, bc.PROD_SERV_ADDR, bc.PROD_SERV_ZIP, bc.PROD_SERV_STATE, " +
            " bc.CHARGE_SRC, bc.LONG_DESCRIPTION, bc.BILL_SERV_CD_INTER_RATE, bc.BILL_SERV_CD_INTRA_RATE " +
            " from bill_compute bc " +
            " join PRODUCT_SERV_DET ps on ps.PRODUCT_SERV_cd=bc.bill_serv_cd and ps.SYSTEM_NAME=bc.SYSTEM_NAME " +
            " join BILL_INVOICE_VERBAGE binvb on binvb.actual_bill_code = bc.bill_serv_cd and binvb.system_name = bc.system_name " +
            " where TRIM(bc.system_name) = TRIM(:system_name) and TRIM(ps.system_name) IN TRIM(:system_name) " +
            " and trunc(bill_date) in (select distinct bill_date from bill_inv_file " +
            " where trunc(bill_run_date) =:bill_date and TRIM(system_name) = TRIM(:system_name) " +
            " and bc.system_name = ps.system_name) " +
            " and ps.PRD_SRV_BEGIN_EFF_DATE <= :bill_date and ps.PRD_SRV_END_EFF_DATE >= :bill_date " +
            " and TRIM(bc.customer_id)=TRIM(:cust_id) and TRIM(bc.invoice_num) = TRIM(:invoice_num)) " +
            " GROUP BY PROD_SERV_RAC, PROD_SERV_RCC, customer_id, state_cd, invoice_num, LEGACY_PRODUCT_CD, " +
            " WBS_CD, COST_CENTER, EXPENDITURE_TYPE_CODE, SYSTEM_NAME, CAST_FIELD, BILL_SERV_CD, " +
            " PROD_SERV_ADDR, PROD_SERV_ZIP, PROD_SERV_STATE, CHARGE_SRC, LONG_DESCRIPTION, " +
            " BILL_SERV_CD_INTER_RATE, BILL_SERV_CD_INTRA_RATE) bcp " +
            "join (select customer_id, entp_id from customer_detail " +
            " where TRIM(system_name) = TRIM(:system_name) and cust_typ_indr = '1') cd " +
            " on trim(cd.customer_id)=trim(bcp.customer_id)", nativeQuery = true)
    List<Object[]> billComputeNativeQueryWithTaxes(@Param("cust_id") String customerId,
            @Param("bill_date") String billDate,
            @Param("invoice_num") String invoiceNumber,
            @Param("system_name") String system_name);

    // billComputeNativeQueryWithoutTaxes — same structure without PROD_SERV_ADDR/ZIP columns

    List<Object[]> billComputeNativeQuery(@Param("cust_id") String customerId,
            @Param("bill_date") String billDate,
            @Param("invoice_num") String invoiceNumber,
            @Param("system_name") String system_name);
}
```

---

## ITEM 2: MbsBrimOutboundService (MBS API)

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/brim/Service/MbsBrimOutboundService.java`

```java
package com.lumen.mbs.brim.Service;

import com.lumen.mbs.model.OutboundJson;
import java.util.List;
import com.lumen.mbs.brim.dto.MbsBrimResponse;
import com.lumen.mbs.brim.outbound.dto.MbsBrimOutboundRequest;
import com.lumen.mbs.brim.outbound.dto.OutboundProcessResponse;
import com.lumen.mbs.brim.outbound.dto.TableRecord;

public interface MbsBrimOutboundService {
    void sendOutboundJsonResponse(MbsBrimResponse mbsBrimResponse, Boolean isBitFile);
    MbsBrimOutboundRequest ValidateAndProcessOutboundData(MbsBrimOutboundRequest mbsBrimOutboundRequest, String mReconInd, String mReconInd2);
    OutboundProcessResponse processOutboundJsonData(MbsBrimOutboundRequest mbsBrimOutboundRequest);
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/brim/Service/MbsBrimOutboundServiceImpl.java` (abbreviated — file is ~900+ lines)

```java
package com.lumen.mbs.brim.Service;

import java.util.List;
import java.util.ListIterator;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import com.lumen.mbs.brim.outbound.config.BrimTransactionConfigService;
import com.lumen.mbs.model.BrimTransactionConfigXref;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lumen.mbs.api.service.BillBalHistoryService;
import com.lumen.mbs.api.service.CustomerService;
import com.lumen.mbs.brim.dto.AddressList;
import com.lumen.mbs.brim.dto.BitInfoList;
import com.lumen.mbs.brim.dto.MbsBrimResponse;
import com.lumen.mbs.brim.dto.TaxItemList;
import com.lumen.mbs.brim.outbound.dto.Adjustments;
import com.lumen.mbs.brim.outbound.dto.MbsBrimOutboundRequest;
import com.lumen.mbs.brim.outbound.dto.OutboundProcessResponse;
import com.lumen.mbs.model.BillAdjustments;
import com.lumen.mbs.model.BillBalHistory;
import com.lumen.mbs.model.BillControl;
import com.lumen.mbs.model.BillInvoiceDetails;
import com.lumen.mbs.model.CustomerDetail;
import com.lumen.mbs.model.OutboundJson;
import com.lumen.mbs.model.OutboundJsonError;
import com.lumen.mbs.model.PaymentDetails;
import com.lumen.mbs.repository.AdjPhraseCodeRepository;
import com.lumen.mbs.repository.BillAdjustmentRepository;
import com.lumen.mbs.repository.BillControlRespository;
import com.lumen.mbs.repository.BillInvoiceRepository;
import com.lumen.mbs.repository.CustomerRepository;
import com.lumen.mbs.repository.OutboundJsonErrorRepository;
import com.lumen.mbs.repository.OutboundJsonRepository;
import com.lumen.mbs.repository.PaymentDetailRepository;
import com.lumen.mbs.brim.outbound.dto.InvoiceDetailsResult;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import com.lumen.mbs.brim.outbound.dto.Payments;
import com.lumen.mbs.brim.outbound.dto.ResponseInfo;
import com.lumen.mbs.brim.outbound.dto.TableRecord;
import com.lumen.mbs.helper.BNCAppConstants;

@Service
public class MbsBrimOutboundServiceImpl implements MbsBrimOutboundService {

    private static final Logger logger = LoggerFactory.getLogger(MbsBrimOutboundServiceImpl.class);

    @Autowired private OutboundJsonRepository outboundJsonRepository;
    @Autowired private OutboundJsonErrorRepository outboundJsonErrorRepository;
    @Autowired private PaymentDetailRepository paymentDetailRepository;
    @Autowired private BillAdjustmentRepository billAdjustmentRepository;
    @Autowired private BillInvoiceRepository billInvoiceRepository;
    @Autowired private MbsBrimHelper mbsBrimHelper;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerService customerService;
    @Autowired private AdjPhraseCodeRepository adjPhraseCodeRepository;
    @Autowired private BillBalHistoryService billBalHistoryService;
    @Autowired private BillControlRespository billControlRespository;
    @Autowired private BrimTransactionConfigService brimTransactionConfigService;
    @Autowired private PaymentService paymentService;
    @Autowired private AdjustmentService adjustmentService;

    private static final String ADJUSTMENT_STATUS_PAID = "PAID";
    private static final String MOD_USER = "BRIMUSR";
    private static final String BLANK = "";
    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final String ERROR_STATUS = "ERROR";
    private static final String AD_DEFAULT_PHRASE_CODE = "BRIM";

    @Override
    public void sendOutboundJsonResponse(MbsBrimResponse mbsBrimResponse, Boolean isBitFile) {
        // Iterates through BitInfoList and TaxItemList, maps all fields to OutboundJson entity,
        // saves to outboundJsonRepository (success) or outboundJsonErrorRepository (error).
        // ~250 lines of field mapping per record.
        // ...
    }

    @Override
    public OutboundProcessResponse processOutboundJsonData(MbsBrimOutboundRequest mbsBrimOutboundRequest) {
        // Processes payments (PY, RV, CB, PM, OP, RF) and adjustments (WO, WR, AM, RC, AD)
        // from the outbound request, saving payment details and bill adjustments.
        // ...
    }

    // Payment processing methods: processPY, processRV, processCB, processPM, processOP, processRF
    // Adjustment processing methods: processWO, processWR, processAM, processRC, processAD
    // Helper: CheckDuplicatePaymentForPY, updateBalaceHistory, validateOnAccount, etc.
}
```

---

### Controller endpoints in `mbs-app-API/src/main/java/com/lumen/mbs/controller/MbsBrimFileController.java`:

```java
@PostMapping(value = "/setOutboundJson", produces = MediaType.APPLICATION_JSON_VALUE)
public void sendOutboundJson(@RequestBody MbsBrimResponse mbsBrimResponse) {
    mbsBrimOutboundService.sendOutboundJsonResponse(mbsBrimResponse, true);
}

@PostMapping(value = "/setOutboundJsonError", produces = MediaType.APPLICATION_JSON_VALUE)
public void sendOutboundJsonError(@RequestBody MbsBrimResponse mbsBrimResponse) {
    mbsBrimOutboundService.sendOutboundJsonResponse(mbsBrimResponse, false);
}

@PostMapping(value = "/processOutbound", produces = MediaType.APPLICATION_JSON_VALUE)
public MbsBrimOutboundRequest processOutbound(@RequestBody MbsBrimOutboundRequestData mbsBrimOutboundRequestData) {
    MbsBrimOutboundRequest mbsBrimOutboundRequest = mbsBrimOutboundRequestData.getMbsBrimOutboundRequest();
    String mReconPrvBalInd = mbsBrimOutboundRequestData.getReconPrevBal();
    String mReconTotDueInd = mbsBrimOutboundRequestData.getReconTotalDue();
    mbsBrimOutboundRequest = mbsBrimOutboundService.ValidateAndProcessOutboundData(mbsBrimOutboundRequest, mReconPrvBalInd, mReconTotDueInd);
    return mbsBrimOutboundRequest;
}

@PostMapping(value = "/setInboundJson", produces = MediaType.APPLICATION_JSON_VALUE)
public void sendInboundJson(@RequestBody MbsBrimOutboundRequest mbsBrimOutboundRequest) {
    mbsBrimInboundService.sendInboundJsonResponse(mbsBrimOutboundRequest, true);
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/brim/dto/MbsBrimResponse.java`

```java
package com.lumen.mbs.brim.dto;

import java.util.Date;

public class MbsBrimResponse {
    private String messageId;
    private String messageTime;
    private String messageType;
    private String messageName;
    private String responseName;
    private String source;
    private String pattern;
    private String version;
    private String uniqueId;
    private String correlationId;
    private Payload payload = new Payload();

    // All getters and setters...
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/brim/outbound/dto/MbsBrimOutboundRequest.java`

```java
package com.lumen.mbs.brim.outbound.dto;

import java.util.Date;

public class MbsBrimOutboundRequest {
    private String messageId;
    private String messageTime;
    private String messageType;
    private String messageName;
    private String source;
    private String pattern;
    private String responseName;
    private String uniqueId;
    private String version;
    private String correlationId;
    private Payload payload = new Payload();

    // All getters and setters...
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/brim/outbound/dto/MbsBrimOutboundRequestData.java`

```java
package com.lumen.mbs.brim.outbound.dto;

public class MbsBrimOutboundRequestData {
    private MbsBrimOutboundRequest mbsBrimOutboundRequest = new MbsBrimOutboundRequest();
    private String ReconPrevBal;
    private String ReconTotalDue;

    public MbsBrimOutboundRequest getMbsBrimOutboundRequest() { return mbsBrimOutboundRequest; }
    public void setMbsBrimOutboundRequest(MbsBrimOutboundRequest mbsBrimOutboundRequest) { this.mbsBrimOutboundRequest = mbsBrimOutboundRequest; }
    public String getReconPrevBal() { return ReconPrevBal; }
    public void setReconPrevBal(String reconPrevBal) { ReconPrevBal = reconPrevBal; }
    public String getReconTotalDue() { return ReconTotalDue; }
    public void setReconTotalDue(String reconTotalDue) { ReconTotalDue = reconTotalDue; }
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/model/OutboundJson.java` (first ~100 lines — entity has 100+ columns)

```java
package com.lumen.mbs.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import javax.persistence.*;

@Entity
@Table(name = "OUTBOUND_JSON")
public class OutboundJson implements Serializable {

    @Id
    @Column(name = "MESSAGEID")
    private String messageId;

    @Column(name = "MESSAGETIME") private String messageTime;
    @Column(name = "MESSAGETYPE") private String messageType;
    @Column(name = "MESSAGENAME") private String messageName;
    @Column(name = "RESPONSENAME") private String responseName;
    @Column(name = "SOURCE") private String source;
    @Column(name = "PATTERN") private String pattern;
    @Column(name = "VERSION") private String version;
    @Column(name = "UNIQUEID") private String uniqueId;
    @Column(name = "CORRELATIONID") private String correlationId;
    @Column(name = "REQUESTID") private String requestId;
    @Column(name = "INPUTCHANNELCD") private String inputChannelCd;
    @Column(name = "SENDTIMESTAMP") private String sendTimestamp;
    @Column(name = "SRCAPPLICATIONCD") private String srcApplicationCd;
    @Column(name = "SRCSYSTRANSACTIONID") private String srcSysTransactionId;
    @Column(name = "INVOICENUMBER") private String invoiceNumber;
    @Column(name = "SRCSYSTEMNAME") private String srcSystemName;
    @Column(name = "INVOICEAMT") private String invoiceAmt;
    @Column(name = "INVOICEDATE") private String invoiceDate;
    @Column(name = "PAYMENTTERMS") private String paymentTerms;
    @Column(name = "BILLACCNO") private String billAccNo;
    @Column(name = "CUSTOMERNUMBER") private String customerNumber;
    @Column(name = "TRANSMISSIONDT") private String transmissionDt;
    @Column(name = "BILLCYCLE") private String billCycle;
    @Column(name = "ACCRUALIND") private String accrualInd;
    @Column(name = "DUEDATE") private String dueDate;
    @Column(name = "POSTINGDATE") private String postingDate;
    @Column(name = "CURRENCYCODE") private String currencyCode;
    // ... 60+ additional columns for charges, taxes, addresses, etc.
}
```

---

## ITEM 3: ProjectEditService (ATC Backend)

---

### File: `atc/backend/src/main/java/com/lumen/atc/service/ProjectEditService.java`

```java
package com.lumen.atc.service;

import com.lumen.atc.constant.ATCConstant;
import com.lumen.atc.dto.*;
import com.lumen.atc.entity.Customer;
import com.lumen.atc.entity.Notes;
import com.lumen.atc.entity.ProjectData;
import com.lumen.atc.json.mbs.BillingRequest;
import com.lumen.atc.repository.CustomerRepository;
import com.lumen.atc.repository.NotesRepository;
import com.lumen.atc.repository.ProjectDataRepository;
import com.lumen.atc.service.adaptors.MBSServiceAdaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectEditService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectEditService.class);

    private static final String CANCELLED_STATUS = ATCConstant.PROJECT_STATUS.CANCELLED.name();
    private static final String OPERATION_CANCEL = "CANCEL_PROJECT";
    private static final String OPERATION_SOW = "UPDATE_STATEMENT_OF_WORK";
    private static final String OPERATION_BILLING = "UPDATE_BILLING_INFO";
    private static final String OPERATION_NEGOTIATED_AMOUNT = "UPDATE_NEGOTIATED_AMOUNT";
    private static final String OPERATION_SUCCESS = "SUCCESS";

    private static final String MBS_SYNC_NOT_REQUIRED = "NOT_REQUIRED";
    private static final String MBS_SYNC_SUCCESS = "SUCCESS";
    private static final String MBS_SYNC_FAILED = "FAILED";

    private static final Set<String> BILLING_FIELDS = Set.of(
            "customerName", "attention", "billAddress", "billCity",
            "billZip", "billEmail", "contactNumber",
            "svcAddress", "svcCity", "svcZip"
    );

    private static final Set<String> SERVICE_ADDRESS_FIELDS = Set.of(
            "svcAddress", "svcCity", "svcZip"
    );

    @Autowired ProjectDataRepository projectDataRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired NotesRepository notesRepository;
    @Autowired WorkflowTransitionService workflowTransitionService;
    @Autowired StateDetailService stateDetailService;
    @Autowired ProjectDataService projectDataService;
    @Autowired MBSServiceAdaptor mbsServiceAdaptor;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired BillingRequestBuilderService billingRequestBuilderService;

    @Transactional(readOnly = true)
    public ProjectEditDetailsResponse getProjectEditDetails(Long projectDataId) {
        logger.info("Loading edit details for projectDataId={}", projectDataId);
        ProjectData projectData = getProject(projectDataId);
        return new ProjectEditDetailsResponse(
                projectData.getProjectDataId(),
                projectData.getProjectId(),
                projectData.getProjectStatus(),
                projectData.getCancelReason(),
                projectData.getStmtOfWork(),
                projectData.getBillNegotiatedAmount(),
                mapBillingInfo(projectData, resolveExistingCustomer(projectData))
        );
    }

    public CompositeProjectEditResponse editProject(Long projectDataId, CompositeProjectEditRequest request) {
        logger.info("Processing composite edit for projectDataId={}", projectDataId);
        validateCompositeRequest(request);

        CompositeEditResult result = transactionTemplate.execute(status -> applyCompositeEdit(projectDataId, request));
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save project edits.");
        }

        syncMbsIfRequired(projectDataId, result);
        return buildCompositeResponse(projectDataId, result);
    }

    private CompositeEditResult applyCompositeEdit(Long projectDataId, CompositeProjectEditRequest request) {
        ProjectData projectData = getProject(projectDataId);
        List<String> appliedOperations = new ArrayList<>();
        LinkedHashSet<String> modifiedFields = new LinkedHashSet<>();
        boolean requiresUpdateSync = false;
        boolean requiresDeleteSync = false;

        if (request.getCancel() != null) {
            applyCancelProjectChange(projectDataId, projectData, request.getCancel());
            appliedOperations.add(OPERATION_CANCEL);
            modifiedFields.add("projectStatus");
            modifiedFields.add("workflowTransition");
            modifiedFields.add("notes");
            requiresDeleteSync = true;
        } else {
            if (request.getStatementOfWork() != null) {
                applyStatementOfWorkChange(projectDataId, projectData, request.getStatementOfWork());
                appliedOperations.add(OPERATION_SOW);
                modifiedFields.add("statementOfWork");
                requiresUpdateSync = true;
            }
            if (request.getBillingInfo() != null) {
                List<String> billingFields = applyBillingInfoChange(projectDataId, projectData, request.getBillingInfo());
                appliedOperations.add(OPERATION_BILLING);
                modifiedFields.addAll(billingFields);
                requiresUpdateSync = true;
            }
            if (request.getNegotiatedAmount() != null) {
                applyNegotiatedAmountChange(projectDataId, projectData, request.getNegotiatedAmount());
                appliedOperations.add(OPERATION_NEGOTIATED_AMOUNT);
                modifiedFields.add("billNegotiatedAmount");
            }
        }

        return new CompositeEditResult(
                projectData.getProjectStatus(),
                appliedOperations,
                new ArrayList<>(modifiedFields),
                requiresUpdateSync,
                requiresDeleteSync
        );
    }

    private void applyCancelProjectChange(Long projectDataId, ProjectData projectData, CancelProjectRequest request) {
        logger.info("Cancelling projectDataId={} by userId={}", projectDataId, request.getUserId());
        if (CANCELLED_STATUS.equalsIgnoreCase(projectData.getProjectStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is already cancelled.");
        }
        String previousStatus = projectData.getProjectStatus();
        projectData.setProjectStatus(CANCELLED_STATUS);
        projectData.setCancelReason(request.getCancellationReason().trim());
        projectDataRepository.save(projectData);
        workflowTransitionService.addWorkflowTransition(projectDataId, previousStatus, CANCELLED_STATUS, request.getUserId());
        saveNotes(projectData, buildNotes(request.getNotes(), "Project is cancelled"));
    }

    private void applyStatementOfWorkChange(Long projectDataId, ProjectData projectData, UpdateStatementOfWorkRequest request) {
        logger.info("Updating statement of work for projectDataId={} by userId={}", projectDataId, request.getUserId());
        projectData.setStmtOfWork(request.getStatementOfWork().trim());
        projectDataRepository.save(projectData);
        saveNotes(projectData, buildNotes(request.getNotes(), "Statement of work updated."));
    }

    private List<String> applyBillingInfoChange(Long projectDataId, ProjectData projectData, UpdateBillingInfoRequest request) {
        logger.info("Updating billing info for projectDataId={} by userId={}", projectDataId, request.getUserId());
        Customer customer = resolveCustomer(projectData, request.getCustomerId());
        List<String> modifiedFields = normalizeBillingFields(request.getModifiedFields());

        for (String field : modifiedFields) {
            switch (field) {
                case "customerName" -> customer.setCustomerName(request.getCustomerName());
                case "attention" -> customer.setAttention(request.getAttention());
                case "billAddress" -> customer.setBillAddress(request.getBillAddress());
                case "billCity" -> customer.setBillCity(request.getBillCity());
                case "billZip" -> customer.setBillZip(request.getBillZip());
                case "billEmail" -> customer.setBillEmail(request.getBillEmail());
                case "contactNumber" -> customer.setContactNumber(request.getContactNumber());
                case "svcAddress" -> projectData.setSvcAddress(request.getSvcAddress());
                case "svcCity" -> projectData.setSvcCity(request.getSvcCity());
                case "svcZip" -> projectData.setSvcZip(request.getSvcZip());
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported billing field: " + field);
            }
        }

        customerRepository.save(customer);
        if (!customer.getCustomerId().equals(projectData.getCustomerId())) {
            projectData.setCustomerId(customer.getCustomerId());
        }
        projectDataRepository.save(projectData);
        saveNotes(projectData, buildNotes(resolveBillingUpdateNotes(request, modifiedFields), buildBillingUpdateDefaultNotes(modifiedFields)));
        return modifiedFields;
    }

    private void applyNegotiatedAmountChange(Long projectDataId, ProjectData projectData, UpdateNegotiatedAmountRequest request) {
        logger.info("Updating negotiated amount for projectDataId={} by userId={}", projectDataId, request.getUserId());
        BigDecimal negotiatedAmount = request.getBillNegotiatedAmount();
        projectData.setBillNegotiatedAmount(negotiatedAmount);
        if (negotiatedAmount != null && negotiatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            projectData.setAmountQuotedCustomer(negotiatedAmount);
        }
        projectDataRepository.save(projectData);
        saveNotes(projectData, buildNotes(request.getNotes(), "Bill negotiated amount updated to " + negotiatedAmount));
    }

    private void validateCompositeRequest(CompositeProjectEditRequest request) {
        if (request == null || !request.hasAnyOperation()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one edit operation is required.");
        }
        if (request.hasCancelOperation() && request.hasNonCancelOperation()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation must be submitted without any other edits.");
        }
    }

    private MbsSyncResult syncMbsIfRequired(Long projectDataId, CompositeEditResult result) {
        if (result.requiresDeleteSync()) {
            return notifyExternalMbsApi(projectDataId, ATCConstant.CustomerOperation.DELETE);
        }
        if (result.requiresUpdateSync()) {
            return notifyExternalMbsApi(projectDataId, ATCConstant.CustomerOperation.UPDATE);
        }
        return MbsSyncResult.notRequired();
    }

    private MbsSyncResult notifyExternalMbsApi(Long projectDataId, ATCConstant.CustomerOperation operation) {
        try {
            BillingRequest billingRequest = new BillingRequest();
            billingRequest.setCustomerBillingRequest(billingRequestBuilderService.prepareCustomerDataForBillingRequest(projectDataId, operation));
            boolean success = mbsServiceAdaptor.submitCustomerBilling(billingRequest);
            if (success) {
                logger.info("MBS sync completed successfully for projectDataId={} operation={}", projectDataId, operation);
                return MbsSyncResult.success(operation.name(), "MBS sync completed successfully.");
            }
            logger.warn("MBS sync returned unsuccessful response for projectDataId={} operation={}", projectDataId, operation);
            return MbsSyncResult.failed(operation.name(), "MBS sync returned an unsuccessful response.");
        } catch (Exception ex) {
            logger.error("MBS sync failed for projectDataId={} operation={}", projectDataId, operation, ex);
            return MbsSyncResult.failed(operation.name(), ex.getMessage());
        }
    }

    // ... helper methods (resolveCustomer, mapBillingInfo, etc.) ...

    private static final class CompositeEditResult {
        private final String projectStatus;
        private final List<String> appliedOperations;
        private final List<String> modifiedFields;
        private final boolean requiresUpdateSync;
        private final boolean requiresDeleteSync;

        private CompositeEditResult(String projectStatus, List<String> appliedOperations,
                List<String> modifiedFields, boolean requiresUpdateSync, boolean requiresDeleteSync) {
            this.projectStatus = projectStatus;
            this.appliedOperations = appliedOperations;
            this.modifiedFields = modifiedFields;
            this.requiresUpdateSync = requiresUpdateSync;
            this.requiresDeleteSync = requiresDeleteSync;
        }

        private String getProjectStatus() { return projectStatus; }
        private List<String> getAppliedOperations() { return appliedOperations; }
        private List<String> getModifiedFields() { return modifiedFields; }
        private boolean requiresUpdateSync() { return requiresUpdateSync; }
        private boolean requiresDeleteSync() { return requiresDeleteSync; }
    }

    private static final class MbsSyncResult {
        private final boolean requested;
        private final String operation;
        private final String status;
        private final String message;

        private MbsSyncResult(boolean requested, String operation, String status, String message) {
            this.requested = requested;
            this.operation = operation;
            this.status = status;
            this.message = message;
        }

        private static MbsSyncResult notRequired() {
            return new MbsSyncResult(false, null, MBS_SYNC_NOT_REQUIRED, "MBS sync not required.");
        }
        private static MbsSyncResult success(String operation, String message) {
            return new MbsSyncResult(true, operation, MBS_SYNC_SUCCESS, message);
        }
        private static MbsSyncResult failed(String operation, String message) {
            return new MbsSyncResult(true, operation, MBS_SYNC_FAILED, message);
        }
    }
}
```

---

### Controller endpoint: `atc/backend/src/main/java/com/lumen/atc/controller/ProjectDataController.java`

```java
@Hidden
@GetMapping("/{projectDataId}/edit")
public ResponseEntity<ProjectEditDetailsResponse> getProjectEditDetails(@PathVariable Long projectDataId) {
    return ResponseEntity.ok(projectEditService.getProjectEditDetails(projectDataId));
}

@Hidden
@PostMapping("/{projectDataId}/edit")
public ResponseEntity<CompositeProjectEditResponse> editProject(
        @PathVariable Long projectDataId,
        @Valid @RequestBody CompositeProjectEditRequest request
) {
    return ResponseEntity.ok(projectEditService.editProject(projectDataId, request));
}
```

---

## ITEM 4: ReportSchedulerService (ATC Backend)

---

### File: `atc/backend/src/main/java/com/lumen/atc/service/ReportSchedulerService.java`

```java
package com.lumen.atc.service;

import com.lumen.atc.entity.ReportConfig;
import com.lumen.atc.repository.ReportConfigRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Loads all active REPORT_CONFIG rows from the DB on startup and registers a
 * CronTrigger-based task for each one.
 *
 * The scheduler is intentionally NOT exposed as a Spring bean to avoid
 * interfering with Spring Boot's auto-configured TaskScheduler that drives
 * existing @Scheduled annotations (e.g. BRIMPaymentScheduler).
 *
 * Call refreshSchedules() at runtime to cancel all existing tasks and
 * re-register from the latest DB state — no application restart needed.
 */
@Service
public class ReportSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(ReportSchedulerService.class);

    @Autowired
    private ReportConfigRepository reportConfigRepository;

    @Autowired
    private ReportExecutorService reportExecutorService;

    private ThreadPoolTaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        logger.info("Initializing ReportSchedulerService...");
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5);
        taskScheduler.setThreadNamePrefix("report-scheduler-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.setAwaitTerminationSeconds(60);
        taskScheduler.initialize();
        logger.info("TaskScheduler initialized with pool size 5");
        refreshSchedules();
    }

    @PreDestroy
    public void destroy() {
        cancelAllTasks();
        if (taskScheduler != null) {
            taskScheduler.shutdown();
            logger.info("Report TaskScheduler shut down.");
        }
    }

    public synchronized void refreshSchedules() {
        logger.info("Refreshing report schedules from database...");
        cancelAllTasks();

        List<ReportConfig> activeConfigs;
        try {
            activeConfigs = reportConfigRepository.findByIsActive("Y");
            logger.info("Loaded {} active report configuration(s)", activeConfigs.size());
        } catch (Exception e) {
            logger.error("Failed to load REPORT_CONFIG from database", e);
            return;
        }

        for (ReportConfig config : activeConfigs) {
            registerSchedule(config);
        }
        logger.info("Report scheduling complete. {} schedule(s) now active", scheduledTasks.size());
    }

    public Map<Long, Boolean> getScheduleStatus() {
        Map<Long, Boolean> status = new ConcurrentHashMap<>();
        scheduledTasks.forEach((id, future) ->
                status.put(id, !future.isCancelled() && !future.isDone()));
        return status;
    }

    private void registerSchedule(ReportConfig config) {
        try {
            CronTrigger trigger = new CronTrigger(config.getCronExpression());
            Long reportId = config.getId();
            String reportName = config.getReportName();

            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> {
                        logger.info("Cron fired: Report '{}' (id={})", reportName, reportId);
                        reportExecutorService.execute(config);
                    },
                    trigger
            );

            scheduledTasks.put(config.getId(), future);
            logger.info("Scheduled report '{}' | cron expression: {}", config.getReportName(), config.getCronExpression());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid cron expression '{}' for report '{}'. This report will NOT be scheduled.",
                config.getCronExpression(), config.getReportName());
        }
    }

    private void cancelAllTasks() {
        if (!scheduledTasks.isEmpty()) {
            logger.debug("Cancelling {} active schedule(s)...", scheduledTasks.size());
            scheduledTasks.forEach((id, future) -> future.cancel(false));
            scheduledTasks.clear();
        }
    }
}
```

---

### File: `atc/backend/src/main/java/com/lumen/atc/service/ReportExecutorService.java`

```java
package com.lumen.atc.service;

import com.lumen.atc.entity.ReportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Orchestrates a single report run:
 *   1. Generate Excel via ReportExcelService
 *   2. Email the result via ReportEmailService
 */
@Service
public class ReportExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(ReportExecutorService.class);

    @Autowired
    private ReportExcelService reportExcelService;

    @Autowired
    private ReportEmailService reportEmailService;

    public void execute(ReportConfig config) {
        logger.info("Executing report: '{}' (id={})", config.getReportName(), config.getId());
        try {
            byte[] excelBytes = reportExcelService.generateReport(config.getSqlQuery(), config.getReportName());

            String subject = (config.getEmailSubject() != null && !config.getEmailSubject().isBlank())
                    ? config.getEmailSubject()
                    : config.getReportName();

            String safeReportName = config.getReportName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
            String dateSuffix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fileName = safeReportName + "_" + dateSuffix + ".xlsx";

            reportEmailService.sendReportEmail(config.getEmailTo(), subject, excelBytes, fileName);
            logger.info("Report '{}' executed successfully. Email sent to: {}",
                config.getReportName(), config.getEmailTo());

        } catch (Exception e) {
            logger.error("Report execution failed for '{}' (id={}): {}",
                config.getReportName(), config.getId(), e.getMessage(), e);
        }
    }
}
```

---

### File: `atc/backend/src/main/java/com/lumen/atc/entity/ReportConfig.java`

```java
package com.lumen.atc.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "REPORT_CONFIG")
@Data
public class ReportConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REPORT_NAME", length = 255, nullable = false)
    private String reportName;

    @Lob
    @Column(name = "SQL_QUERY", nullable = false)
    private String sqlQuery;

    @Column(name = "CRON_EXPRESSION", length = 100, nullable = false)
    private String cronExpression;

    @Column(name = "EMAIL_TO", length = 1000, nullable = false)
    private String emailTo;

    @Column(name = "EMAIL_SUBJECT", length = 500)
    private String emailSubject;

    @Column(name = "IS_ACTIVE", length = 1, nullable = false)
    private String isActive;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "CREATED_DTTM")
    private LocalDateTime createdDttm;

    @Column(name = "LAST_MODIFIED_BY", length = 100)
    private String lastModifiedBy;

    @Column(name = "LAST_MODIFIED_DTTM")
    private LocalDateTime lastModifiedDttm;

    // All getters and setters...
}
```

---

### File: `atc/backend/src/main/java/com/lumen/atc/repository/ReportConfigRepository.java`

```java
package com.lumen.atc.repository;

import com.lumen.atc.entity.ReportConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportConfigRepository extends JpaRepository<ReportConfig, Long> {
    List<ReportConfig> findByIsActive(String isActive);
}
```

---

### File: `atc/backend/src/main/java/com/lumen/atc/controller/ReportSchedulerController.java`

```java
package com.lumen.atc.controller;

import com.lumen.atc.entity.ReportConfig;
import com.lumen.atc.repository.ReportConfigRepository;
import com.lumen.atc.service.ReportExecutorService;
import com.lumen.atc.service.ReportSchedulerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/reports")
@Tag(name = "Report Scheduler", description = "Admin APIs to manage and trigger DB-driven scheduled reports")
public class ReportSchedulerController {

    private static final Logger logger = LoggerFactory.getLogger(ReportSchedulerController.class);

    @Autowired private ReportSchedulerService reportSchedulerService;
    @Autowired private ReportExecutorService reportExecutorService;
    @Autowired private ReportConfigRepository reportConfigRepository;

    @Operation(summary = "Refresh report schedules")
    @PostMapping("/refresh-schedules")
    public ResponseEntity<String> refreshSchedules() {
        reportSchedulerService.refreshSchedules();
        return ResponseEntity.ok("Report schedules refreshed successfully.");
    }

    @Operation(summary = "Manually trigger a report")
    @PostMapping("/trigger/{id}")
    public ResponseEntity<String> triggerReport(@PathVariable Long id) {
        Optional<ReportConfig> configOpt = reportConfigRepository.findById(id);
        if (configOpt.isEmpty()) {
            logger.warn("Report id={} not found", id);
            return ResponseEntity.notFound().build();
        }
        ReportConfig config = configOpt.get();
        reportExecutorService.execute(config);
        return ResponseEntity.ok("Report '" + config.getReportName() + "' (id=" + id + ") triggered successfully.");
    }

    @Operation(summary = "Get report schedule status")
    @GetMapping("/schedule-status")
    public ResponseEntity<Map<Long, Boolean>> getScheduleStatus() {
        return ResponseEntity.ok(reportSchedulerService.getScheduleStatus());
    }
}
```

---

## ITEM 5: Bill Rerun Functionality

---

### "Rerun" class/controller search result:

**No class with "rerun" in its name exists.** The rerun functionality is embedded within existing classes:

- **Controller:** `BNCController.java` in MBS GUI
- **Service:** `MBSBillPullDetailServiceImpl.java`
- **Repository:** `MBSBillPullDetailRepository.java`
- **JSP:** `BillCompare.jsp`
- **Entity:** `MBSBillPullDetail.java` (has `BILL_RERUN_INDR` column)

---

### File: `mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/controller/BNCController.java` (rerun-related methods, lines 690–725)

```java
@GetMapping(path = "/getAvailableSystemsForBillReRun", produces = MediaType.APPLICATION_JSON_VALUE)
public @ResponseBody List<Map<String, String>> getAvailableSystemsForBillReRun(
        @RequestParam("billPullDate") String aBillPullDate) {

    try {
        Date mBillPullDate = TimestampUtil.getDateFromISO8601String(aBillPullDate);
        List<String> systems = billPullService.getAvailableSystemsForBillReRun(mBillPullDate);

        List<Map<String, String>> result = systems.stream().map(systemName -> {
            Map<String, String> map = new HashMap<>();
            map.put("systemName", systemName);
            return map;
        }).collect(Collectors.toList());

        return result;
    } catch (Exception e) {
        logger.error("Exception in getAvailableSystemsForBillReRun:", e);
        return new ArrayList<>();
    }
}

@PostMapping(path = "/updateBillReRunIndrToY", produces = MediaType.APPLICATION_JSON_VALUE)
public @ResponseBody int updateBillReRunIndrToY(@RequestParam("billPullDate") String aBillPullDate,
        @RequestBody List<String> systemNames) {
    int rowUpdated = 0;
    try {
        Date mBillPullDate = TimestampUtil.getDateFromISO8601String(aBillPullDate);
        rowUpdated = billPullService.updateBillReRunIndrToYForMultipleSystems(mBillPullDate, systemNames);
    } catch (Exception e) {
        logger.error("Exception in updateBillReRunIndrToY:", e);
    }
    return rowUpdated;
}
```

---

### File: `mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/repository/MBSBillPullDetailRepository.java` (rerun-related queries)

```java
@Query(
    "SELECT DISTINCT d.systemName " +
    "FROM MBSBillPullDetail d " +
    "WHERE d.systemName IN ( " +
    "  SELECT DISTINCT c.systemName " +
    "  FROM BillControl c " +
    "  WHERE c.billPullDate = :billPullDate " +
    ") " +
    "AND d.billRelIndr = 'Y' " +
    "AND d.billRunIndr  = 'Y' " +
    "AND d.billMediaIndr = 'N' " +
    "AND d.billPullDate = :billPullDate"
)
List<String> getAvailableSystemsForBillReRunAndReleaseMedia(@Param("billPullDate") Date billPullDate);

@Transactional
@Modifying
@Query("UPDATE MBSBillPullDetail a SET billReRunIndr = 'Y' WHERE a.billPullDate = :billPullDate AND TRIM(a.systemName) IN :systemNames")
int updateBillReRunIndrToYForMultipleSystems(@Param("billPullDate") Date billPullDate,
        @Param("systemNames") List<String> systemNames);
```

---

### File: `mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/model/MBSBillPullDetail.java`

```java
package com.ctl.mbs.model;

import java.io.Serializable;
import java.sql.Date;
import javax.persistence.*;

@Entity
@IdClass(MBSBillPullDetailPk.class)
@Table(name = "BILLPULL_DETAIL")
public class MBSBillPullDetail implements Serializable {

    private static final long serialVersionUID = -1798070786993154676L;

    @Id
    @Column(name = "BILL_PULL_DATE")
    private Date billPullDate;

    @Column(name = "BILL_RUN_DATE")
    private Date billRunDate;

    @Column(name = "BILL_MEDIA_DATE")
    private Date billMediaDate;

    @Column(name = "BILL_RUN_INDR")
    private String billRunIndr;

    @Column(name = "BILL_MEDIA_INDR")
    private String billMediaIndr;

    @Column(name = "BILL_REL_INDR")
    private String billRelIndr;

    @Column(name = "EDW_REL_INDR")
    private String edwRelIndr;

    @Column(name = "JRNL_REL_INDR")
    private String jrnlRelIndr;

    @Id
    @Column(name = "SYSTEM_NAME")
    private String systemName;

    @Column(name = "BILL_RERUN_INDR")
    private String billReRunIndr;

    // All getters and setters...
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/model/BillControl.java`

```java
package com.lumen.mbs.model;

import java.util.Date;
import javax.persistence.*;

@Entity
@IdClass(BillControlPK.class)
@Table(name = "BILL_CONTROL")
public class BillControl {

    @Id
    @Column(name = "CUSTOMER_ID")
    private String custId;

    @Column(name = "BILL_TYPE")
    private String billType;

    @Id
    @Column(name = "BILL_PULL_DATE")
    private Date billPullDate;

    @Column(name = "BILL_COMPLETE")
    private String billComplete;

    @Id
    @Column(name = "SYSTEM_NAME")
    private String systemName;

    // All getters and setters...
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/model/BillControlPK.java`

```java
package com.lumen.mbs.model;

import java.io.Serializable;
import java.util.Date;

public class BillControlPK implements Serializable {

    private String custId;
    private Date billPullDate;
    private String systemName;

    public BillControlPK() { super(); }
    public BillControlPK(String custId, Date billPullDate, String systemName) {
        this.custId = custId;
        this.billPullDate = billPullDate;
        this.systemName = systemName;
    }

    // Getters, setters, hashCode(), equals()...
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/repository/BillControlRespository.java`

```java
package com.lumen.mbs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.lumen.mbs.model.BillControl;
import com.lumen.mbs.model.BillControlPK;

import java.util.Date;
import java.util.List;
import javax.transaction.Transactional;

@Repository
public interface BillControlRespository extends JpaRepository<BillControl, BillControlPK> {

    @Query(value ="SELECT DISTINCT(bc.billPullDate) FROM BillControl bc WHERE TRIM(bc.systemName)= TRIM(:systemName) ORDER BY bc.billPullDate DESC ")
    List<Date> getBillControlBillPullDate(@Param("systemName") String systemName);

    @Query(value ="SELECT DISTINCT(bc.custId) FROM BillControl bc WHERE bc.billPullDate= :billPullDate AND TRIM(bc.systemName)= TRIM(:systemName)")
    List<String> getBillControlCustomerID(@Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Query(value ="SELECT bc FROM BillControl bc WHERE TRIM(bc.custId)= TRIM(:custId) AND bc.billPullDate= :billPullDate AND TRIM(bc.systemName)= TRIM(:systemName)")
    BillControl getBillControlDetails(@Param("custId") String custId, @Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Transactional
    @Modifying
    @Query("delete from BillControl a where TRIM(a.custId)= TRIM(:custId) AND a.billPullDate= :billPullDate AND TRIM(a.systemName)= TRIM(:systemName)")
    void deleteBillControlDetails(@Param("custId") String custId, @Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Query(value ="SELECT bc FROM BillControl bc WHERE TRIM(bc.custId)= TRIM(:custId) AND TRIM(bc.systemName)= TRIM(:systemName)")
    List<BillControl> getBillControlDetailsList(@Param("custId") String custId, @Param("systemName") String systemName);

    @Query(value ="SELECT bc FROM BillControl bc WHERE TRIM(bc.custId)= TRIM(:custId) AND bc.billPullDate = :billPullDate AND TRIM(bc.systemName)= TRIM(:systemName)")
    List<BillControl> getBillControlDetail(@Param("custId") String custId, @Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);
}
```

---

### File: `mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/controller/BillControlController.java`

```java
package com.ctl.mbs.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.ctl.mbs.dto.BillControlDTO;
import com.ctl.mbs.service.BillControlService;
import com.ctl.mbs.util.TimestampUtil;

@Controller
public class BillControlController {

    private static Logger logger = LoggerFactory.getLogger(BillControlController.class);

    @Autowired
    private BillControlService billControlService;

    @GetMapping(path = "/getBillControlBillPullDate", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody List<String> getBillControlBillPullDate(
            @RequestParam("asystemName") String asystemName, HttpServletRequest aRequest) {
        List<String> billPullDate = new ArrayList<String>();
        try {
            billPullDate = billControlService.getBillControlBillPullDate(asystemName);
        } catch (Exception e) {
            logger.error("Exception in getCustomerID:", e);
        }
        return billPullDate;
    }

    @GetMapping(path = "/getBillControlCustomerID", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody List<String> getBillControlCustomerID(
            @RequestParam("billPullDate") String aBillPullDate,
            @RequestParam("asystemName") String asystemName, HttpServletRequest aRequest) {
        List<String> customerIds = new ArrayList<String>();
        Date mBillPullDate = TimestampUtil.getDateFromISO8601String(aBillPullDate);
        try {
            customerIds = billControlService.getBillControlCustomerID(mBillPullDate, asystemName);
        } catch (Exception e) {
            logger.error("Exception in getCustomerID:", e);
        }
        return customerIds;
    }

    @GetMapping(path = "/getBillControlDetails", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody BillControlDTO getBillControlDetails(
            @RequestParam("custID") String aCustomerID,
            @RequestParam("billPullDate") String aBillPullDate,
            @RequestParam("asystemName") String asystemName) {
        BillControlDTO mBillControl = new BillControlDTO();
        try {
            mBillControl = billControlService.getBillControlDetails(aCustomerID, aBillPullDate, asystemName);
        } catch (Exception e) {
            logger.error("Exception in getPastOccDetails:", e);
        }
        return mBillControl;
    }

    @PostMapping(path = "/updateBillControl", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody String updateBillControl(@RequestBody BillControlDTO aBillControl, HttpServletRequest aRequest) {
        String Status = null;
        try {
            Status = billControlService.updateBillControl(aBillControl);
        } catch (Exception e) {
            logger.error("Exception in updateBillControl:", e);
        }
        return Status;
    }

    @PostMapping(path = "/addBillControl", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody String addBillControl(@RequestBody BillControlDTO aBillControl, HttpServletRequest aRequest) {
        String mIsAdded = null;
        try {
            mIsAdded = billControlService.addBillControl(aBillControl);
        } catch (Exception e) {
            logger.error("Exception in addBillControl:", e);
        }
        return mIsAdded;
    }
}
```

---

### File: `mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/service/BillControlServiceImpl.java`

```java
package com.ctl.mbs.service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ctl.mbs.dto.BillControlDTO;
import com.ctl.mbs.model.BillControl;
import com.ctl.mbs.repository.BCAdjustmentRepository;
import com.ctl.mbs.repository.BillControlRespository;
import com.ctl.mbs.repository.PaymentDetailRepository;
import com.ctl.mbs.util.TimestampUtil;

@Service
public class BillControlServiceImpl implements BillControlService {
    private static Logger logger = LoggerFactory.getLogger(BillControlServiceImpl.class);

    @Autowired private BillControlRespository billControlRespository;
    @Autowired private BCAdjustmentRepository bCAdjustmentRepository;
    @Autowired private PaymentDetailRepository paymentDetailRepository;

    @Override
    public List<String> getBillControlBillPullDate(String systemName) {
        List<String> mBillControlDate = new ArrayList<String>();
        try {
            List<Date> billControlDate = billControlRespository.getBillControlBillPullDate(systemName);
            for (Date bpDate : billControlDate) {
                mBillControlDate.add(TimestampUtil.convertDateToISOString(bpDate));
            }
        } catch (Exception e) {
            logger.error("Exception in getBillControlBillPullDate:", e);
        }
        return mBillControlDate;
    }

    @Override
    public List<String> getBillControlCustomerID(Date billPullDate, String systemName) {
        List<String> customerIds = new ArrayList<String>();
        try {
            customerIds = billControlRespository.getBillControlCustomerID(billPullDate, systemName);
        } catch (Exception e) {
            logger.error("Exception in getBillControlCustomerID:", e);
        }
        return customerIds;
    }

    @Override
    public BillControlDTO getBillControlDetails(String custId, String billPullDate, String systemName) {
        BillControlDTO mBillControlDTO = new BillControlDTO();
        DateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy");
        String billDateFormatted = dateFormat.format(TimestampUtil.getDateFromISO8601String(billPullDate));
        BillControl mBillControl = billControlRespository.getBillControlDetails(custId, billDateFormatted, systemName);

        mBillControlDTO.setCustId(mBillControl.getCustId());
        mBillControlDTO.setBillType(mBillControl.getBillType());
        mBillControlDTO.setBillPullDate(TimestampUtil.convertDateToISOString(mBillControl.getBillPullDate()));
        mBillControlDTO.setBillComplete(mBillControl.getBillComplete());
        mBillControlDTO.setSystemName(mBillControl.getSystemName());
        return mBillControlDTO;
    }

    @Override
    public String updateBillControl(BillControlDTO aBillControlDTO) {
        String mStatus = null;
        try {
            String mBillPullDate = aBillControlDTO.getBillPullDate();
            DateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy");
            String billDateFormatted = dateFormat.format(TimestampUtil.getDateFromISO8601String(mBillPullDate));
            BillControl mBillControl = billControlRespository.getBillControlDetails(
                    aBillControlDTO.getCustId(), billDateFormatted, aBillControlDTO.getSystemName());

            if (aBillControlDTO.getBillType().equalsIgnoreCase("Approve")) {
                mBillControl.setBillType("A");
                billControlRespository.save(mBillControl);
                mStatus = "Updated";
            } else if (aBillControlDTO.getBillType().equalsIgnoreCase("Reject")) {
                mBillControl.setBillType("W");
                mStatus = "Updated";
                billControlRespository.save(mBillControl);
            } else if (aBillControlDTO.getBillType().equalsIgnoreCase("Hold")) {
                mBillControl.setBillType("H");
                mStatus = "Updated";
                billControlRespository.save(mBillControl);
            } else if (aBillControlDTO.getBillType().equalsIgnoreCase("Delete")) {
                billControlRespository.delete(mBillControl);
                mStatus = "Deleted";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mStatus;
    }

    @Override
    public String addBillControl(BillControlDTO aBillControlDTO) {
        String mIsAdded = null;
        try {
            BillControl mBillcontrol = new BillControl();
            Date mBillPullDate = TimestampUtil.getDateFromISO8601String(aBillControlDTO.getBillPullDate());

            mBillcontrol.setCustId(aBillControlDTO.getCustId());
            mBillcontrol.setBillPullDate(mBillPullDate);
            mBillcontrol.setSystemName(aBillControlDTO.getSystemName());

            if (aBillControlDTO.getBillType().equalsIgnoreCase("true")) {
                mBillcontrol.setBillType("A");
            } else {
                mBillcontrol.setBillType("W");
            }
            mBillcontrol.setBillComplete("N");

            billControlRespository.save(mBillcontrol);
            paymentDetailRepository.UpdatePaymentEffectiveDate(aBillControlDTO.getCustId(), mBillPullDate, aBillControlDTO.getSystemName());
            bCAdjustmentRepository.updateAdjustmentEffectiveDate(aBillControlDTO.getCustId(), mBillPullDate, aBillControlDTO.getSystemName());

            mIsAdded = "yes";
        } catch (Exception e) {
            mIsAdded = "no";
            e.printStackTrace();
        }
        return mIsAdded;
    }
}
```

---

# Bill Rerun Source Code Extraction

**Branch:** `batch_brimrelo_stabilization` (mbs-app-API)

---

## 1. Rerun Service Interface, Implementation, and Helper

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/batch/service/BatchBillRerunService.java`

```java
package com.lumen.mbs.batch.service;

import com.lumen.mbs.batch.dto.BillRerunResult;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public interface BatchBillRerunService {

    BillRerunResult runBillReRunForSystem(String systemName);
    boolean runBillReRunForCustomer(String customerId, Date billPullDate, String systemName);
    boolean billReRun(String systemName, Date billPullDate);
    boolean billReRunForCustomer(String systemName, Date billPullDate, String customerId);
    boolean isBillReRunRequired(String systemName, Date billPullDate);
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/batch/service/BatchBillRerunServiceImpl.java`

```java
package com.lumen.mbs.batch.service;


import com.lumen.mbs.batch.common.constant.MbsBatchConstants;
import com.lumen.mbs.batch.common.utils.BatchCommonModuleService;
import com.lumen.mbs.batch.common.utils.DynamicLogger;
import com.lumen.mbs.batch.common.utils.EmailService;
import com.lumen.mbs.batch.dto.BillRerunResult;
import com.lumen.mbs.batch.exception.InvoiceNotFoundException;
import com.lumen.mbs.batch.service.helper.BatchBillRerunHelper;
import com.lumen.mbs.model.MBSBillPullDetail;
import com.lumen.mbs.model.MbsAppConfigDetail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.lumen.mbs.batch.common.constant.MbsBatchConstants.CHARGE_SRC_USAGE;

@Service
public class BatchBillRerunServiceImpl extends BatchBillRerunHelper implements BatchBillRerunService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BatchBillRerunServiceImpl.class);

    @Autowired
    private DynamicLogger logger;

    @Autowired
    private BatchCommonModuleService commonService;

    @Autowired
    private EmailService emailService;

    /**
     * Rerun billing for all eligible customers in this system for the latest bill date.
     */
    public BillRerunResult runBillReRunForSystem(String systemName) {
        long overallStartTime = System.currentTimeMillis();
        logger.logInfo(log, "Starting bill rerun process for system: " + systemName);
    	
    	MbsAppConfigDetail email = commonService.getEmailForBilling();
        // Validate system name
        if (!commonService.isSystemValid(systemName)) {
            logger.logWarn(log, "Invalid system name argument passed: " + systemName);
			emailService.sendEmail("Billing", "MbsBillReRun failed For " + systemName +
                    "!!!! Failed !!! Please Check", email.getEmailFrom(), email.getEmailTo());
            logger.logInfo(log, "Failure notification email sent for invalid system name: " + systemName);
            throw new IllegalArgumentException("Invalid system name");
        }

        // Fetch all eligible bill pull details for rerun
        List<MBSBillPullDetail> billPullDetails;
        try {
            billPullDetails = getBillReRunIndicator(systemName);
        } catch (Exception e) {
            logger.logError(log, "Error fetching bill rerun indicators for system: " + systemName, e);
            throw new RuntimeException("Error fetching bill rerun indicators", e);
        }

        if (billPullDetails == null || billPullDetails.isEmpty()) {
            logger.logInfo(log, "No bill cycles eligible for rerun for system: " + systemName);
            return new BillRerunResult(new ArrayList<>(), new ArrayList<>(), false);
        }

        List<Date> processedCycles = new ArrayList<>();
        List<Date> failedCycles = new ArrayList<>();
        boolean hasErrors = false;

        for (MBSBillPullDetail detail : billPullDetails) {
            Date billDate = detail.getBillPullDate();
            try {
                boolean success = billReRun(systemName, billDate);

                if (success) {
                    processedCycles.add(billDate);
                } else {
                    hasErrors = true;
                    failedCycles.add(billDate);
                    logger.logWarn(log, "Bill rerun completed with errors for cycle: " + billDate);
                }

            } catch (InvoiceNotFoundException ex) {
                hasErrors = true;
                failedCycles.add(billDate);
                logger.logError(log, "Invoice not found for bill cycle " + billDate + ": " + ex.getMessage(), ex);
            } catch (Exception ex) {
                hasErrors = true;
                failedCycles.add(billDate);
                emailService.sendEmail("Billing", "MbsBillReRun failed For " + systemName +
                        "!!!! Failed !!! Please Check", email.getEmailFrom(), email.getEmailTo());
                logger.logError(log, "Unexpected error in bill rerun for system " + systemName + ": " + ex.getMessage(), ex);
            }
        }
        
        long overallDuration = System.currentTimeMillis() - overallStartTime;
        if (hasErrors) {
            logger.logWarn(log, "System bill rerun completed with errors for " + systemName + ": " + processedCycles.size() + 
                " cycles processed successfully, some cycles failed. Duration: " + overallDuration + " ms (" + (overallDuration / 1000.0) + " seconds)");
        } else {
            logger.logInfo(log, "System bill rerun completed successfully for " + systemName + ": " + processedCycles.size() + 
                " cycles processed in " + overallDuration + " ms (" + (overallDuration / 1000.0) + " seconds)");
        }
        
        return new BillRerunResult(processedCycles, failedCycles, hasErrors);
    }

    /**
     * Rerun billing for a single customer.
     */
    public boolean runBillReRunForCustomer(String customerId, Date billPullDate, String systemName) {
        long overallStartTime = System.currentTimeMillis();
        logger.logInfo(log, "Starting customer bill rerun for customer: " + customerId + ", system: " + systemName + ", billDate: " + billPullDate);
    	
    	MbsAppConfigDetail email = commonService.getEmailForBilling();
        if (!commonService.isSystemValid(systemName)) {
        	emailService.sendEmail("Billing", "MbsBillReRun failed For " + systemName +
                    "!!!! Failed !!! Please Check", email.getEmailFrom(), email.getEmailTo());
            throw new IllegalArgumentException("Invalid system name");
        }

        try {
            boolean success = billReRunForCustomer(systemName, billPullDate, customerId);
            long overallDuration = System.currentTimeMillis() - overallStartTime;
            if (success) {
                logger.logInfo(log, "Customer bill rerun completed successfully for " + customerId + ": " +
                        overallDuration + " ms (" + (overallDuration / 1000.0) + " seconds)");
            } else {
                logger.logWarn(log, "Customer bill rerun completed with errors for " + customerId + ": " +
                        overallDuration + " ms (" + (overallDuration / 1000.0) + " seconds)");
            }
            return success;
        } catch (InvoiceNotFoundException ex) {
            logger.logError(log, "Invoice not found for customer rerun: " + ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
        	emailService.sendEmail("Billing", "MbsBillReRun failed For " + systemName +
                    "!!!! Failed !!! Please Check", email.getEmailFrom(), email.getEmailTo());
            logger.logError(log, "Unexpected error in bill rerun for customer " + customerId + ": " + ex.getMessage(), ex);
            throw new RuntimeException("Bill ReRun failed for customer: " + customerId, ex);
        }
    }

    @Override
    @Transactional
    public boolean billReRun(String systemName, Date billPullDate) {
        long startTime = System.currentTimeMillis();
        logger.logInfo(log, "=== BILL RE-RUN (SYSTEM BULK) STARTING === System: " + systemName + ", Bill Date: " + billPullDate);
        resetCounters();
        boolean overallSuccess = true;

        // Check if any usage records exist for this bill date and system
        long totalBillUsageCount = billComputeRepository
                .countUsageBillComputeByDateAndSystem(billPullDate, systemName, CHARGE_SRC_USAGE);
        long totalExpenseUsageCount = expenseComputeRepository.
                countUsageExpenseComputeByDateAndSystem(billPullDate, systemName, CHARGE_SRC_USAGE);

        logger.logInfo(log, "Total bill usage count for system " + systemName + " and bill date " +
                billPullDate + ": " + totalBillUsageCount);
        logger.logInfo(log, "Total expense usage count for system " + systemName + " and bill date " +
                billPullDate + ": " + totalExpenseUsageCount);

        if (!deleteBillComputeDetails(billPullDate, systemName)) {
            overallSuccess = false;
        }
        if (!deleteBillCompute(billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (!deleteExpenseComputeDetails(billPullDate, systemName)) {
            overallSuccess = false;
        }
        if (!deleteExpenseCompute(billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (!deleteTaxComputeDetails(billPullDate, systemName)) {
            overallSuccess = false;
        }
        if (!deleteTaxCompute(billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (!deleteBillInvoiceFiles(billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (!deleteAssignDetails(billPullDate)) {
            overallSuccess = false;
        }

        if (!deleteExpenseVouchers(billPullDate)) {
            overallSuccess = false;
        }

        if (!updatePaymentsAndAdjustments(billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (!deleteAffiliateAPAYs(billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (totalBillUsageCount > 0 || totalExpenseUsageCount > 0) {
            // Fetch distinct customer IDs from BillControl for the given bill date and system name
            List<String> customerIds = billControlRepository.getBillControlCustomerID(billPullDate, systemName);
            for (String customerId : customerIds) {
                    if (!updateUsageIndr(customerId, billPullDate, systemName, String.valueOf(MbsBatchConstants.afterProcess),
                            String.valueOf(MbsBatchConstants.beforeProcess))) {
                        logger.logWarn(log, "Reverting usage process indicator failed due to cycle date mismatch for customer: " + customerId + ", system: " + systemName + ", billDate: " + billPullDate + ". Skipping this customer.");
                    }
            }
        }

        if (!updateBillControlUsingDateAndSystem(billPullDate, systemName)) {
            overallSuccess = false;
        }

        //Update bill pull detail if all successful
        if (overallSuccess) {
            boolean updateSuccess = updateBillPullDetail(billPullDate, systemName);
            if (!updateSuccess) {
                logger.logError(log, "Failed to update bill pull detail for system=" + systemName + ", billPullDate=" + billPullDate, null);
                overallSuccess = false;
            } else {
                logger.logInfo(log, "Updated bill pull detail for system=" + systemName + ", billPullDate=" + billPullDate);
            }
        } else {
            logger.logInfo(log, "Skipping bill pull detail update due to errors in rerun");
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.logInfo(log, "=== BILL RE-RUN (SYSTEM BULK) COMPLETED === Duration: " + duration + " ms (" + (duration / 1000.0) + " seconds), Status: " + (overallSuccess ? "SUCCESS" : "ERROR"));

        logBillRerunSummary(systemName, billPullDate, overallSuccess);
        return overallSuccess;
    }

    @Override
    @Transactional
    public boolean billReRunForCustomer(String systemName, Date billPullDate, String customerId) {
        long startTime = System.currentTimeMillis();
        logger.logInfo(log, "=== BILL RE-RUN (CUSTOMER) STARTING === Customer: " + customerId + ", System: " + systemName + ", Bill Date: " + billPullDate);

        resetCounters();
        boolean overallSuccess = true;

        long billUsageCount = billComputeRepository
                .countUsageBillComputeByCustIdDateAndSystem(customerId, billPullDate, systemName, CHARGE_SRC_USAGE);
        long expenseUsageCount = expenseComputeRepository
                .countUsageExpenseComputeByCustIdDateAndSystem(customerId, billPullDate, systemName, CHARGE_SRC_USAGE);

        logger.logInfo(log, "Bill usage count for customer " + customerId + ", system " + systemName + ", and bill date " +
                billPullDate + ": " + billUsageCount);
        logger.logInfo(log, "Expense usage count for customer " + customerId + ", system " + systemName + ", and bill date " +
                billPullDate + ": " + expenseUsageCount);

        if (!deleteBillComputeDetails(customerId, billPullDate, systemName)) {
            overallSuccess = false;
        }
        if (!deleteBillCompute(customerId, billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (!deleteExpenseComputeDetails(customerId, billPullDate, systemName)) {
            overallSuccess = false;
        }
        if (!deleteExpenseCompute(customerId, billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (!deleteTaxComputeDetails(customerId, billPullDate, systemName)) {
            overallSuccess = false;
        }
        if (!deleteTaxCompute(customerId, billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (!deleteBillInvoiceFiles(customerId, billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (!deleteAssignDetails(customerId, billPullDate)) {
            overallSuccess = false;
        }

        if (!deleteExpenseVouchers(customerId, billPullDate)) {
            overallSuccess = false;
        }

        if (!updatePaymentsAndAdjustments(customerId, billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (!deleteAffiliateAPAYs(customerId, billPullDate, systemName)) {
            overallSuccess = false;
        }

        if (billUsageCount > 0 || expenseUsageCount > 0) {
                if (!updateUsageIndr(customerId, billPullDate, systemName, String.valueOf(MbsBatchConstants.afterProcess),
                        String.valueOf(MbsBatchConstants.beforeProcess))) {
                     logger.logWarn(log, "Reverting usage process indicator failed due to cycle date mismatch" +
                             " for customer: " + customerId + ", system: " + systemName + ", billDate: " + billPullDate
                             + ". Skipping this customer.");
                }
        }

        if (!updateBillControl(customerId, billPullDate, systemName)) {
            overallSuccess = false;
        }

        // Update bill pull detail if successful
        if (overallSuccess) {
            if (!updateBillPullDetail(billPullDate, systemName)) {
                logger.logError(log, "Failed to update bill pull detail for system=" + systemName + ", billPullDate=" + billPullDate, null);
                overallSuccess = false;
            } else {
                logger.logInfo(log, "Updated bill pull detail for system=" + systemName + ", billPullDate=" + billPullDate);
            }
        } else {
            logger.logInfo(log, "Skipping bill pull detail update due to errors in rerun for customer " + customerId);
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.logInfo(log, "=== BILL RE-RUN (CUSTOMER) COMPLETED === Customer: " + customerId + ", Duration: " + duration + " ms (" + (duration / 1000.0) + " seconds), Status: " + (overallSuccess ? "SUCCESS" : "ERROR"));

        logBillRerunSummary(systemName, billPullDate, overallSuccess);
        return overallSuccess;
    }

    @Override
    public boolean isBillReRunRequired(String systemName, Date billPullDate) {
        return super.isBillReRunRequired(systemName, billPullDate);
    }

}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/batch/service/helper/BatchBillRerunHelper.java`

```java
package com.lumen.mbs.batch.service.helper;

import com.lumen.mbs.batch.common.utils.BatchBillingCycleDateService;
import com.lumen.mbs.batch.common.utils.DynamicLogger;
import com.lumen.mbs.model.BillControl;
import com.lumen.mbs.model.CustomerDetail;
import com.lumen.mbs.model.MBSBillPullDetail;
import com.lumen.mbs.repository.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static com.lumen.mbs.batch.common.constant.MbsBatchConstants.*;

public class BatchBillRerunHelper {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BatchBillRerunHelper.class);

    @Autowired
    protected DynamicLogger logger;

    @Autowired protected PaymentDetailRepository paymentRepository;
    @Autowired protected BillAdjustmentRepository adjustmentRepository;
    @Autowired protected MBSBillComputeRepository billComputeRepository;
    @Autowired protected ExpenseComputeRepository expenseComputeRepository;
    @Autowired protected ExpenseVoucherRepository expenseVoucherRepository;
    @Autowired protected MBSBillPullDetailRepository billPullDetailRepository;
    @Autowired protected BillControlRespository billControlRepository;
    @Autowired protected CustomerRepository customerRepository;
    @Autowired protected BillInvoiceRepository billInvoiceRepository;
    @Autowired protected TaxComputeRepository taxComputeRepository;
    @Autowired protected BillComputeDetailRepository billComputeDetailRepository;
    @Autowired protected ExpenseComputeDetailRepository expenseComputeDetailRepository;
    @Autowired protected MbsBrimTaxRepository mbsBrimTaxRepository;
    @Autowired protected CurrUsageRepository currUsageRepository;
    @Autowired protected BatchBillingCycleDateService batchBillingCycleDateService;
    @Autowired protected AssignDetailsRepository assignDetailsRepository;

    // Global counters for tracking total rows affected
    protected int cntBillComputeDetailDeleted = 0;
    protected int cntBillComputeDeleted = 0;
    protected int cntExpenseComputeDetailDeleted = 0;
    protected int cntExpenseComputeDeleted = 0;
    protected int cntTaxComputeDetailDeleted = 0;
    protected int cntTaxComputeDeleted = 0;
    protected int cntBillBalanceHistoryDeleted = 0;
    protected int cntBillInvoiceFilesDeleted = 0;
    protected int cntAssignDetailsDeleted = 0;
    protected int cntExpenseVouchersDeleted = 0;
    protected int cntPaymentsUpdated = 0;
    protected int cntAdjustmentsUpdated = 0;
    protected int cntAffiliateAPAYsDeleted = 0;
    protected int cntUsageProcessUpdated = 0;
    protected int cntBillControlUpdated = 0;
    protected int cntBillPullDetailUpdated = 0;
    protected int totNumOfRowsUpdCurrUsg = 0;

    protected void resetCounters() {
        cntBillComputeDetailDeleted = 0;
        cntBillComputeDeleted = 0;
        cntExpenseComputeDetailDeleted = 0;
        cntExpenseComputeDeleted = 0;
        cntTaxComputeDetailDeleted = 0;
        cntTaxComputeDeleted = 0;
        cntBillBalanceHistoryDeleted = 0;
        cntBillInvoiceFilesDeleted = 0;
        cntAssignDetailsDeleted = 0;
        cntExpenseVouchersDeleted = 0;
        cntPaymentsUpdated = 0;
        cntAdjustmentsUpdated = 0;
        cntAffiliateAPAYsDeleted = 0;
        cntUsageProcessUpdated = 0;
        cntBillControlUpdated = 0;
        cntBillPullDetailUpdated = 0;
        totNumOfRowsUpdCurrUsg = 0;
    }

    protected void logBillRerunSummary(String systemName, Date billPullDate, boolean overallSuccess) {
        logger.logInfo(log, "Bill rerun summary for system=" + systemName + ", billPullDate=" + billPullDate + 
                ": Deleted: BillComputeDetail=" + cntBillComputeDetailDeleted + ", BillCompute=" + cntBillComputeDeleted + 
                ", ExpenseComputeDetail=" + cntExpenseComputeDetailDeleted + ", ExpenseCompute=" + cntExpenseComputeDeleted +
                ", TaxComputeDetail=" + cntTaxComputeDetailDeleted + ", TaxCompute=" + cntTaxComputeDeleted + 
                ", BillBalanceHistory=" + cntBillBalanceHistoryDeleted + ", BillInvoiceFiles=" + cntBillInvoiceFilesDeleted +
                ", AssignDetails=" + cntAssignDetailsDeleted + ", ExpenseVouchers=" + cntExpenseVouchersDeleted +
                ", AffiliateAPAYs=" + cntAffiliateAPAYsDeleted +
                "; Updated: Payments=" + cntPaymentsUpdated + ", Adjustments=" + cntAdjustmentsUpdated +
                ", UsageProcess=" + cntUsageProcessUpdated + ", BillControl=" + cntBillControlUpdated + 
                ", BillPullDetail=" + cntBillPullDetailUpdated + ", CurrUsage=" + totNumOfRowsUpdCurrUsg);

        if (overallSuccess) {
            logger.logInfo(log, "=== BILL RE-RUN PROCESS END [SUCCESS] === System: " + systemName + ", Bill Date: " + billPullDate);
        } else {
            logger.logInfo(log, "=== BILL RE-RUN PROCESS END [ERROR] === System: " + systemName + ", Bill Date: " + billPullDate);
        }
    }

    protected boolean deleteBillComputeDetails(Date billPullDate, String systemName) {
        try {
            int deleted = billComputeDetailRepository.deleteBillComputeDetailByBillDateAndSystemName(billPullDate, systemName);
            cntBillComputeDetailDeleted += deleted;
            logger.logInfo(log, "Deleted " + deleted + " BillComputeDetail records");
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting BillComputeDetail: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteBillComputeDetails(String customerId, Date billPullDate, String systemName) {
        try {
            int deleted = billComputeDetailRepository.deleteBillComputeDetailByBillDateSystemNameAndCustomerId(billPullDate, customerId, systemName);
            cntBillComputeDetailDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting BillComputeDetail for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteBillCompute(Date billPullDate, String systemName) {
        try {
            int deleted = billComputeRepository.deleteBillComputeByBillDateAndSystemName(billPullDate, systemName);
            cntBillComputeDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting BillCompute: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteBillCompute(String customerId, Date billPullDate, String systemName) {
        try {
            int deleted = billComputeRepository.deleteBillComputeByBillDateSystemNameAndCustomerId(billPullDate, customerId, systemName);
            cntBillComputeDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting BillCompute for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteExpenseComputeDetails(Date billPullDate, String systemName) {
        try {
            int deleted = expenseComputeDetailRepository.deleteExpenseComputeDetailByBillDateAndSystemName(billPullDate, systemName);
            cntExpenseComputeDetailDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting ExpenseComputeDetail: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteExpenseComputeDetails(String customerId, Date billPullDate, String systemName) {
        try {
            int deleted = expenseComputeDetailRepository.deleteExpenseComputeDetailByBillDateSystemNameAndCustomerId(billPullDate, customerId, systemName);
            cntExpenseComputeDetailDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting ExpenseComputeDetail for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteExpenseCompute(Date billPullDate, String systemName) {
        try {
            int deleted = expenseComputeRepository.deleteExpenseComputeByBillDateAndSystemName(billPullDate, systemName);
            cntExpenseComputeDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting ExpenseCompute: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteExpenseCompute(String customerId, Date billPullDate, String systemName) {
        try {
            int deleted = expenseComputeRepository.deleteExpenseComputeByBillDateSystemNameAndCustomerId(billPullDate, customerId, systemName);
            cntExpenseComputeDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting ExpenseCompute for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteTaxComputeDetails(Date billPullDate, String systemName) {
        try {
            int deleted = mbsBrimTaxRepository.deleteTaxComputeDetailByBillDateAndSystemName(billPullDate, systemName);
            cntTaxComputeDetailDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting TaxComputeDetail: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteTaxComputeDetails(String customerId, Date billPullDate, String systemName) {
        try {
            int deleted = mbsBrimTaxRepository.deleteTaxComputeDetailByBillDateSystemNameAndCustomerId(billPullDate, customerId, systemName);
            cntTaxComputeDetailDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting TaxComputeDetail for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteTaxCompute(Date billPullDate, String systemName) {
        try {
            int deleted = taxComputeRepository.deleteTaxComputeByBillDateAndSystemName(billPullDate, systemName);
            cntTaxComputeDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting TaxCompute: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteTaxCompute(String customerId, Date billPullDate, String systemName) {
        try {
            int deleted = taxComputeRepository.deleteTaxComputeByBillDateSystemNameAndCustomerId(billPullDate, customerId, systemName);
            cntTaxComputeDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting TaxCompute for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteBillInvoiceFiles(Date billPullDate, String systemName) {
        try {
            int deleted = billInvoiceRepository.deleteBillInvoiceFileByBillDateAndSystemName(billPullDate, systemName);
            cntBillInvoiceFilesDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting BillInvoiceFiles: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteBillInvoiceFiles(String customerId, Date billPullDate, String systemName) {
        try {
            int deleted = billInvoiceRepository.deleteBillInvoiceFileByBillDateSystemNameAndCustomerId(billPullDate, customerId, systemName);
            cntBillInvoiceFilesDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting BillInvoiceFiles for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteAssignDetails(Date billPullDate) {
        try {
            List<String> invoiceNumbers = expenseVoucherRepository.findInvoiceNumbersByVoucherDate(billPullDate);
            if (invoiceNumbers.isEmpty()) {
                return true;
            }
            List<String> trimmedInvoiceNumbers = invoiceNumbers.stream().map(String::trim).collect(java.util.stream.Collectors.toList());
            int totalDeleted = assignDetailsRepository.deleteByAssignedInvoiceIn(trimmedInvoiceNumbers);
            cntAssignDetailsDeleted += totalDeleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting AssignDetails: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteAssignDetails(String customerId, Date billPullDate) {
        try {
            List<String> invoiceNumbers = expenseVoucherRepository.findInvoiceNumbersByVoucherDateAndCustomerId(billPullDate, customerId);
            if (invoiceNumbers.isEmpty()) {
                return true;
            }
            List<String> trimmedInvoiceNumbers = invoiceNumbers.stream().map(String::trim).collect(java.util.stream.Collectors.toList());
            int totalDeleted = assignDetailsRepository.deleteByAssignedInvoiceIn(trimmedInvoiceNumbers);
            cntAssignDetailsDeleted += totalDeleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting AssignDetails for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteExpenseVouchers(Date billPullDate) {
        try {
            int deleted = expenseVoucherRepository.deleteExpenseVoucherByVoucherDateAndSystemName(billPullDate);
            cntExpenseVouchersDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting ExpenseVouchers: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteExpenseVouchers(String customerId, Date billPullDate) {
        try {
            int deleted = expenseVoucherRepository.deleteExpenseVoucherByVoucherDateSystemNameAndCustomerId(billPullDate, customerId);
            cntExpenseVouchersDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting ExpenseVouchers for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean updatePaymentsAndAdjustments(Date billPullDate, String systemName) {
        try {
            int updatedPayments = paymentRepository.updateBillPaymentDetailsByDateAndSys(billPullDate, INV_STATUS_PAID, systemName);
            cntPaymentsUpdated += updatedPayments;
            int updatedAdjustments = adjustmentRepository.updateBillAdjustmentDetailsByBillPullDateAndSystem(billPullDate, INV_STATUS_PAID, systemName);
            cntAdjustmentsUpdated += updatedAdjustments;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error updating Payments and Adjustments: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean updatePaymentsAndAdjustments(String customerId, Date billPullDate, String systemName) {
        try {
            int updatedPayments = paymentRepository.updateBillPaymentDetailsByDateSysAndCustomer(customerId, billPullDate, INV_STATUS_PAID, systemName);
            cntPaymentsUpdated += updatedPayments;
            int updatedAdjustments = adjustmentRepository.updateBillAdjustmentDetailsByBillDateSysAndCustomer(customerId, billPullDate, INV_STATUS_PAID, systemName);
            cntAdjustmentsUpdated += updatedAdjustments;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error updating Payments and Adjustments for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteAffiliateAPAYs(Date billPullDate, String systemName) {
        try {
            int deleted = paymentRepository.deleteAffiliateAPAYsByPaymentDateAndSystemName(billPullDate, MODE_OF_PAYMENT_APAY, systemName);
            cntAffiliateAPAYsDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting Affiliate APAYs: " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean deleteAffiliateAPAYs(String customerId, Date billPullDate, String systemName) {
        try {
            int deleted = paymentRepository.deleteAPAYsByPaymentDateSysAndCustomer(billPullDate, customerId, MODE_OF_PAYMENT_APAY, systemName);
            cntAffiliateAPAYsDeleted += deleted;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error deleting APAYs for affiliate customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean updateBillControlUsingDateAndSystem(Date billPullDate, String systemName) {
        try {
            int updated = updateBillControlEntities(billPullDate, systemName);
            cntBillControlUpdated += updated;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error updating BillControl: " + e.getMessage(), e);
            return false;
        }
    }

    public int updateBillControlEntities(Date billPullDate, String systemName) {
        List<BillControl> billControlList = billControlRepository.findByBillPullDateAndSystemName(billPullDate, systemName);
        int updated = 0;
        for (BillControl billControl : billControlList) {
                billControl.setBillComplete(BILL_COMPLETE_NO);
                billControl.setBillType(BILL_COMPLETE_NO);
                billControl.setRatingHold(BILL_COMPLETE_NO);
                billControl.setTaxingHold(BILL_COMPLETE_NO);
                billControl.setBillHold(BILL_COMPLETE_NO);
                billControl.setFormattingHold(BILL_COMPLETE_NO);
                billControlRepository.save(billControl);
                updated++;
        }
        return updated;
    }

    protected boolean updateBillControl(String customerId, Date billPullDate, String systemName) {
        try {
            List<BillControl> billControlList = billControlRepository.findByCustIdAndBillPullDateAndSystemName(customerId, billPullDate, systemName);
            int updated = 0;
            for (BillControl billControl : billControlList) {
                    billControl.setBillComplete(BILL_COMPLETE_NO);
                    billControl.setBillType(BILL_COMPLETE_NO);
                    billControl.setRatingHold(BILL_COMPLETE_NO);
                    billControl.setTaxingHold(BILL_COMPLETE_NO);
                    billControl.setBillHold(BILL_COMPLETE_NO);
                    billControl.setFormattingHold(BILL_COMPLETE_NO);
                    billControlRepository.save(billControl);
                    updated++;
            }
            cntBillControlUpdated += updated;
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error updating BillControl for customer " + customerId + ": " + e.getMessage(), e);
            return false;
        }
    }

    protected boolean updateBillPullDetail(Date billPullDate, String systemName) {
        try {
            List<MBSBillPullDetail> mbsBillPullDetails = billPullDetailRepository.findByBillPullDateAndSystemName(billPullDate, systemName);
            int updated = 0;
            for (MBSBillPullDetail billPullDetail : mbsBillPullDetails) {
                billPullDetail.setBillRunIndr(INDICATOR_NO);
                billPullDetail.setBillRelIndr(INDICATOR_YES);
                billPullDetail.setBillReRunIndr(INDICATOR_NO);
                billPullDetailRepository.save(billPullDetail);
                updated++;
            }
            return true;
        } catch (Exception e) {
            logger.logError(log, "Error updating BillPullDetail rerun indicators: " + e.getMessage(), e);
            return false;
        }
    }

    protected List<MBSBillPullDetail> getBillReRunIndicator(String systemName) {
        return billPullDetailRepository.findBillReRunIndicator(systemName);
    }

    protected MBSBillPullDetail getValidBillPullDetailForSystem(String systemName) {
        List<MBSBillPullDetail> indicatorList = getBillReRunIndicator(systemName);
        if (indicatorList == null || indicatorList.size() != 1) {
            throw new IllegalArgumentException("No valid bill re-run indicator details found for system: " + systemName);
        }
        return indicatorList.get(0);
    }

    protected boolean isBillReRunRequired(String systemName, Date billPullDate) {
        if (billPullDate == null) {
            return false;
        }

        List<MBSBillPullDetail> indicatorList = getBillReRunIndicator(systemName);
        if (indicatorList == null || indicatorList.isEmpty())
            return false;

        for (MBSBillPullDetail billPullDetail : indicatorList) {
            boolean isIndicatorYes = INDICATOR_YES.equalsIgnoreCase(billPullDetail.getBillReRunIndr());
            boolean isBillPullDateNotNull = billPullDetail.getBillPullDate() != null;

            boolean isBillPullDateEqual = false;
            if (isBillPullDateNotNull) {
                LocalDate detailDate = billPullDetail.getBillPullDate().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                LocalDate inputDate = billPullDate.toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                isBillPullDateEqual = detailDate.equals(inputDate);
            }

            if (isIndicatorYes && isBillPullDateEqual) {
                return true;
            }
        }
        return false;
    }

    protected BatchBillingCycleDateService.UsageCycleDateRange calculateUsgEndDate(String customerId, String systemName, Date currBillPullDate) {
        if (customerId == null || systemName == null || currBillPullDate == null) {
            return null;
        }

        List<CustomerDetail> eligibleCustomers = customerRepository.getCustomerDetailList(customerId, systemName);
        if (eligibleCustomers == null || eligibleCustomers.isEmpty()) {
            return null;
        }

        CustomerDetail custUsgCycDet = eligibleCustomers.get(0);
        if (custUsgCycDet.getCustStatus().equalsIgnoreCase(CUST_STATUS_INACTIVE)) {
            return null;
        }

        BatchBillingCycleDateService.UsageCycleDateRange usageWindow = batchBillingCycleDateService
                .calculateUsageCycleDates(custUsgCycDet, currBillPullDate);

        if (usageWindow == null || usageWindow.getEndDate() == null) {
            return null;
        }
        return usageWindow;
    }

    protected boolean updateUsageIndr(String customerId, Date billDate, String systemName, String beforeProcess, String afterProcess) {
        boolean success = true;
        int updCount;
        try {
            BatchBillingCycleDateService.UsageCycleDateRange usageCycleDateRange = calculateUsgEndDate(customerId, systemName, billDate);
            if (usageCycleDateRange == null) {
                return false;
            }

            updCount = currUsageRepository.UpdateCurrentUsage(
                    customerId,
                    usageCycleDateRange.getBeginDate(),
                    usageCycleDateRange.getEndDate(),
                    systemName,
                    beforeProcess,
                    afterProcess
            );
            totNumOfRowsUpdCurrUsg += updCount;
        } catch (Exception e) {
            logger.logError(log, "Error updating usage indicator for customer=" + customerId + ": " + e.getMessage(), e);
            success = false;
        }
        return success;
    }
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/batch/dto/BillRerunResult.java`

```java
package com.lumen.mbs.batch.dto;

import java.util.Date;
import java.util.List;

public class BillRerunResult {
    
    private List<Date> successfulCycles;
    private List<Date> failedCycles;
    private boolean hasErrors;
    private String errorMessage;
    
    public BillRerunResult() {}
    
    public BillRerunResult(List<Date> successfulCycles, List<Date> failedCycles, boolean hasErrors) {
        this.successfulCycles = successfulCycles;
        this.failedCycles = failedCycles;
        this.hasErrors = hasErrors;
    }
    
    public List<Date> getSuccessfulCycles() { return successfulCycles; }
    public void setSuccessfulCycles(List<Date> successfulCycles) { this.successfulCycles = successfulCycles; }
    public List<Date> getFailedCycles() { return failedCycles; }
    public void setFailedCycles(List<Date> failedCycles) { this.failedCycles = failedCycles; }
    public boolean isHasErrors() { return hasErrors; }
    public void setHasErrors(boolean hasErrors) { this.hasErrors = hasErrors; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public boolean isCompleteSuccess() {
        return !hasErrors && (failedCycles == null || failedCycles.isEmpty());
    }
    
    public boolean isPartialSuccess() {
        return hasErrors && (successfulCycles != null && !successfulCycles.isEmpty());
    }
    
    public boolean isCompleteFailure() {
        return hasErrors && (successfulCycles == null || successfulCycles.isEmpty());
    }
}
```

---

## 2. BillControl Entity (with stage-completion flags)

### File: `mbs-app-API/src/main/java/com/lumen/mbs/model/BillControl.java`

```java
package com.lumen.mbs.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

@Entity
@IdClass(BillControlPK.class)
@Table(name = "BILL_CONTROL")
public class BillControl {

    @Id
    @Column(name = "CUSTOMER_ID")
    private String custId;

    @Column(name = "BILL_TYPE")
    private String billType;

    @Id
    @Column(name = "BILL_PULL_DATE")
    private Date billPullDate;

    @Column(name = "BILL_COMPLETE")
    private String billComplete;

    @Id
    @Column(name = "SYSTEM_NAME")
    private String systemName;

    @Column(name = "RATING_HOLD")
    private String ratingHold;

    @Column(name = "BILLING_HOLD")
    private String billHold;

    @Column(name = "TAXING_HOLD")
    private String taxingHold;

    @Column(name = "FORMATTING_HOLD")
    private String formattingHold;

    public String getBillHold() { return billHold; }
    public void setBillHold(String billHold) { this.billHold = billHold; }
    public String getCustId() { return custId; }
    public void setCustId(String custId) { this.custId = custId; }
    public String getBillType() { return billType; }
    public void setBillType(String billType) { this.billType = billType; }
    public Date getBillPullDate() { return billPullDate; }
    public void setBillPullDate(Date billPullDate) { this.billPullDate = billPullDate; }
    public String getBillComplete() { return billComplete; }
    public void setBillComplete(String billComplete) { this.billComplete = billComplete; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }
    public String getRatingHold() { return ratingHold; }
    public void setRatingHold(String ratingHold) { this.ratingHold = ratingHold; }
    public String getTaxingHold() { return taxingHold; }
    public void setTaxingHold(String taxingHold) { this.taxingHold = taxingHold; }
    public String getFormattingHold() { return formattingHold; }
    public void setFormattingHold(String formattingHold) { this.formattingHold = formattingHold; }
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/model/BillControlPK.java`

```java
package com.lumen.mbs.model;

import java.io.Serializable;
import java.util.Date;

public class BillControlPK implements Serializable {

    private String custId;
    private Date billPullDate;
    private String systemName;

    public BillControlPK() { super(); }
    public BillControlPK(String custId, Date billPullDate, String systemName) {
        this.custId = custId;
        this.billPullDate = billPullDate;
        this.systemName = systemName;
    }

    public String getCustId() { return custId; }
    public void setCustId(String custId) { this.custId = custId; }
    public Date getBillPullDate() { return billPullDate; }
    public void setBillPullDate(Date billPullDate) { this.billPullDate = billPullDate; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((billPullDate == null) ? 0 : billPullDate.hashCode());
        result = prime * result + ((custId == null) ? 0 : custId.hashCode());
        result = prime * result + ((systemName == null) ? 0 : systemName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        BillControlPK other = (BillControlPK) obj;
        if (billPullDate == null) { if (other.billPullDate != null) return false; }
        else if (!billPullDate.equals(other.billPullDate)) return false;
        if (custId == null) { if (other.custId != null) return false; }
        else if (!custId.equals(other.custId)) return false;
        if (systemName == null) { if (other.systemName != null) return false; }
        else if (!systemName.equals(other.systemName)) return false;
        return true;
    }
}
```

---

## 3. BillControlRepository

### File: `mbs-app-API/src/main/java/com/lumen/mbs/repository/BillControlRespository.java`

```java
package com.lumen.mbs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.lumen.mbs.model.BillControl;
import com.lumen.mbs.model.BillControlPK;

import java.util.Date;
import java.util.List;

import javax.transaction.Transactional;

@Repository
public interface BillControlRespository extends JpaRepository<BillControl, BillControlPK> {

    @Query(value = "SELECT DISTINCT(bc.billPullDate) FROM BillControl bc WHERE TRIM(bc.systemName)= TRIM(:systemName) ORDER BY bc.billPullDate DESC ")
    List<Date> getBillControlBillPullDate(@Param("systemName") String systemName);

    @Query(value = "SELECT DISTINCT(bc.custId) FROM BillControl bc WHERE bc.billPullDate= :billPullDate AND TRIM(bc.systemName)= TRIM(:systemName)")
    List<String> getBillControlCustomerID(@Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Query(value = "SELECT bc FROM BillControl bc WHERE TRIM(bc.custId)= TRIM(:custId) AND bc.billPullDate= :billPullDate AND TRIM(bc.systemName)= TRIM(:systemName)")
    BillControl getBillControlDetails(@Param("custId") String custId, @Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Transactional
    @Modifying
    @Query("delete from BillControl a where TRIM(a.custId)= TRIM(:custId) AND a.billPullDate= :billPullDate AND TRIM(a.systemName)= TRIM(:systemName)")
    void deleteBillControlDetails(@Param("custId") String custId, @Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Query(value = "SELECT bc FROM BillControl bc WHERE TRIM(bc.custId)= TRIM(:custId) AND TRIM(bc.systemName)= TRIM(:systemName)")
    List<BillControl> getBillControlDetailsList(@Param("custId") String custId, @Param("systemName") String systemName);

    @Query(value = "SELECT bc FROM BillControl bc WHERE TRIM(bc.custId)= TRIM(:custId) AND bc.billPullDate = :billPullDate AND TRIM(bc.systemName)= TRIM(:systemName)")
    List<BillControl> getBillControlDetail(@Param("custId") String custId, @Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    /********** Batch Queries *************/

    @Transactional
    @Modifying
    @Query("update BillControl bc set bc.billHold = 'Y' where TRIM(bc.custId) = TRIM(:custId) and TRIM(bc.systemName) = TRIM(:systemName) and TRUNC(bc.billPullDate) = TRUNC(:billPullDate)")
    int completeBillingForCustomerId(@Param("custId") String custId, @Param("systemName") String systemName, @Param("billPullDate") Date billPullDate);

    @Transactional
    @Modifying
    @Query("update BillControl bc set bc.billHold = 'H', bc.billComplete = 'N' where TRIM(bc.custId) = TRIM(:custId) and TRIM(bc.systemName) = TRIM(:systemName) and TRUNC(bc.billPullDate) = TRUNC(:billPullDate)")
    int holdBillingForCustomerId(@Param("custId") String custId, @Param("systemName") String systemName, @Param("billPullDate") Date billPullDate);

    @Query(value = "SELECT DISTINCT bc.CUSTOMER_ID AS customerId, bp.bill_pull_date AS billPullDate "
            + "FROM bill_control bc "
            + "JOIN billpull_detail bp ON TO_CHAR(bc.bill_pull_date, 'YYYY-MM-DD') = TO_CHAR(bp.bill_pull_date, 'YYYY-MM-DD') "
            + "WHERE TO_CHAR(bp.bill_pull_date, 'YYYY-MM-DD') = TO_CHAR(:billPullDate, 'YYYY-MM-DD') AND (bc.BILL_TYPE IN ('A' , 'N')) "
            + "AND TRIM(bc.system_name) = :systemName AND TRIM(bc.bill_complete) = :processValBefore "
            + "AND TRIM(bp.bill_run_indr) = :processValBefore AND TRIM(bp.bill_media_indr) = :processValBefore "
            + "AND TRIM(bp.bill_rel_indr) = :processValAfter", nativeQuery = true)
    List<Object[]> GetMbsCustomersFromBCBP(@Param("billPullDate") Date billPullDate,
            @Param("systemName") String systemName, @Param("processValBefore") String processValBefore,
            @Param("processValAfter") String processValAfter);

    @Query(value = "SELECT trim(customer_id) FROM bill_control WHERE "
            + " bill_complete = 'Y' "
            + " AND TRUNC(bill_pull_date) = :billPullDate "
            + " AND TRIM(system_name) = :systemName "
            + " AND billing_hold = 'Y' AND rating_hold = 'Y' ", nativeQuery = true)
    List<String> findEligibleCustomersForFormatting(@Param("systemName") String systemName, @Param("billPullDate") Date billPullDate);

    @Query(value = "SELECT * FROM bill_control WHERE "
            + " TRUNC(bill_pull_date) = :billPullDate "
            + " AND TRIM(system_name) = TRIM(:systemName) "
            + " AND TRIM(customer_id) = TRIM(:customerId) ", nativeQuery = true)
    BillControl getBillControl(@Param("customerId") String customerId, @Param("systemName") String systemName, @Param("billPullDate") Date billPullDate);

    @Query("SELECT bc FROM BillControl bc WHERE TRIM(bc.custId) = TRIM(:custId) AND TRUNC(bc.billPullDate) = :billPullDate " +
            "AND TRIM(bc.systemName) = TRIM(:systemName)")
    List<BillControl> findByCustIdAndBillPullDateAndSystemName(String custId, Date billPullDate, String systemName);

    @Query("SELECT bc FROM BillControl bc WHERE TRUNC(bc.billPullDate) = :billPullDate " +
            "AND TRIM(bc.systemName) = TRIM(:systemName)")
    List<BillControl> findByBillPullDateAndSystemName(Date billPullDate, String systemName);
}
```

---

## 4. MBSBillPullDetail Entity and Repository (stage flags + rerun indicator)

### File: `mbs-app-API/src/main/java/com/lumen/mbs/model/MBSBillPullDetail.java`

```java
package com.lumen.mbs.model;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.*;

@Entity
@IdClass(MBSBillPullDetailPk.class)
@Table(name = "BILLPULL_DETAIL")
public class MBSBillPullDetail implements Serializable {

    private static final long serialVersionUID = -1798070786993154676L;

    @Id
    @Column(name = "BILL_PULL_DATE")
    private Date billPullDate;

    @Column(name = "BILL_RUN_DATE")
    private Date billRunDate;

    @Column(name = "BILL_MEDIA_DATE")
    private Date billMediaDate;

    @Column(name = "BILL_RUN_INDR")
    private String billRunIndr;

    @Column(name = "BILL_MEDIA_INDR")
    private String billMediaIndr;

    @Column(name = "BILL_REL_INDR")
    private String billRelIndr;

    @Column(name = "EDW_REL_INDR")
    private String edwRelIndr;

    @Column(name = "JRNL_REL_INDR")
    private String jrnlRelIndr;

    @Id
    @Column(name = "SYSTEM_NAME")
    private String systemName;

    @Column(name = "BILL_RERUN_INDR")
    private String billReRunIndr;

    // All getters and setters for every field...
    public Date getBillPullDate() { return billPullDate; }
    public void setBillPullDate(Date billPullDate) { this.billPullDate = billPullDate; }
    public Date getBillRunDate() { return billRunDate; }
    public void setBillRunDate(Date billRunDate) { this.billRunDate = billRunDate; }
    public Date getBillMediaDate() { return billMediaDate; }
    public void setBillMediaDate(Date billMediaDate) { this.billMediaDate = billMediaDate; }
    public String getBillRunIndr() { return billRunIndr; }
    public void setBillRunIndr(String billRunIndr) { this.billRunIndr = billRunIndr; }
    public String getBillMediaIndr() { return billMediaIndr; }
    public void setBillMediaIndr(String billMediaIndr) { this.billMediaIndr = billMediaIndr; }
    public String getBillRelIndr() { return billRelIndr; }
    public void setBillRelIndr(String billRelIndr) { this.billRelIndr = billRelIndr; }
    public String getJrnlRelIndr() { return jrnlRelIndr; }
    public void setJrnlRelIndr(String jrnlRelIndr) { this.jrnlRelIndr = jrnlRelIndr; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }
    public String getEdwRelIndr() { return edwRelIndr; }
    public void setEdwRelIndr(String edwRelIndr) { this.edwRelIndr = edwRelIndr; }
    public String getBillReRunIndr() { return billReRunIndr; }
    public void setBillReRunIndr(String billReRunIndr) { this.billReRunIndr = billReRunIndr; }
}
```

---

### File: `mbs-app-API/src/main/java/com/lumen/mbs/repository/MBSBillPullDetailRepository.java`

```java
package com.lumen.mbs.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.lumen.mbs.model.MBSBillPullDetail;
import com.lumen.mbs.model.MBSBillPullDetailPk;

@Repository
public interface MBSBillPullDetailRepository extends JpaRepository<MBSBillPullDetail, MBSBillPullDetailPk> {

    @Query("SELECT a.billPullDate FROM MBSBillPullDetail a, CustomerDetail b WHERE trim(b.custBillCycle) = TO_CHAR(a.billPullDate,'DD') AND TO_CHAR(SYSDATE, 'MM') = TO_CHAR(a.billPullDate,'MM') AND a.billRunIndr = 'N' AND a.billRelIndr = 'N' AND b.custId = :custId AND TRIM(b.systemName)= TRIM(:systemName) AND TRIM(a.systemName)= TRIM(:systemName) ORDER BY a.billPullDate ASC")
    Date getMBSBillPullDate(@Param("custId") String custId, @Param("systemName") String systemName);

    @Query("SELECT a.billPullDate FROM MBSBillPullDetail a, CustomerDetail b WHERE trim(b.custBillCycle) = TO_CHAR(a.billPullDate,'DD') AND TO_CHAR(add_months(SYSDATE, 1), 'MM') = TO_CHAR(a.billPullDate,'MM') AND a.billRunIndr = 'N' AND a.billRelIndr = 'N' AND b.custId = :custId AND TRIM(b.systemName)= TRIM(:systemName) AND TRIM(a.systemName)= TRIM(:systemName) ORDER BY a.billPullDate ASC")
    Date getMBSBillPullDateNextMonth(@Param("custId") String custId, @Param("systemName") String systemName);

    @Query("SELECT a.billPullDate FROM MBSBillPullDetail a, CustomerDetail b WHERE trim(b.custBillCycle) = TO_CHAR(a.billPullDate,'DD') AND TO_CHAR(SYSDATE, 'MM') = TO_CHAR(a.billPullDate,'MM') AND a.billRunIndr = 'N' AND a.billRelIndr = 'N' AND b.custId = :custId AND TRIM(b.systemName)= TRIM(:systemName) AND TRIM(a.systemName)= TRIM(:systemName) ORDER BY a.billPullDate ASC")
    Date getMBSBillRunDate(@Param("custId") String custId, @Param("systemName") String systemName);

    @Query("SELECT a FROM MBSBillPullDetail a WHERE a.billRunDate <= SYSDATE AND a.billMediaIndr = 'N' AND TRIM(a.systemName)= TRIM(:systemName) ORDER BY a.billPullDate ASC")
    List<MBSBillPullDetail> getBillPullDateForRlseIndr(@Param("systemName") String systemName);

    @Transactional
    @Modifying
    @Query("UPDATE MBSBillPullDetail a SET billRelIndr = 'Y' where a.billPullDate = :billPullDate AND TRIM(a.systemName)= TRIM(:systemName)")
    int UpdateRelIndrToY(@Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Transactional
    @Modifying
    @Query("UPDATE MBSBillPullDetail a SET billRelIndr = 'N' where a.billPullDate = :billPullDate AND TRIM(a.systemName)= TRIM(:systemName)")
    int UpdateRelIndrToN(@Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Transactional
    @Modifying
    @Query("UPDATE MBSBillPullDetail a SET billMediaIndr = 'Y' where a.billPullDate = :billPullDate AND TRIM(a.systemName)= TRIM(:systemName)")
    int UpdateMediaIndrToY(@Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Query("SELECT a FROM MBSBillPullDetail a WHERE a.billPullDate = :billPullDt")
    List<MBSBillPullDetail> getBillPullDet(@Param("billPullDt") Date billPullDate);

    @Query("SELECT CASE WHEN COUNT(b) >0 THEN true ELSE false END FROM MBSBillPullDetail b WHERE b.billPullDate = :billPullDate AND b.billRelIndr = 'Y' AND TRIM(b.systemName)= TRIM(:systemName)")
    boolean BillPullDateReleased(@Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Query("SELECT a FROM MBSBillPullDetail a WHERE a.billPullDate = :billPullDate AND TRIM(a.systemName)= TRIM(:systemName)")
    MBSBillPullDetail getBillPullDate(@Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Transactional
    @Modifying
    @Query("delete from MBSBillPullDetail a where a.billPullDate = :billPullDate AND TRIM(a.systemName)= TRIM(:systemName)")
    void deleteBillPullDetail(@Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);

    @Query("SELECT a.billPullDate FROM MBSBillPullDetail a, CustomerDetail b WHERE trim(b.custBillCycle) = TO_CHAR(a.billPullDate,'DD') AND TO_CHAR(SYSDATE, 'MM') = TO_CHAR(a.billPullDate,'MM') AND TO_CHAR(SYSDATE, 'YYYY') = TO_CHAR(a.billPullDate,'YYYY') AND a.billRunIndr = 'N' AND TRIM(b.custId) = TRIM(:custId) AND TRIM(b.systemName)= TRIM(:systemName) AND TRIM(a.systemName)= TRIM(:systemName) ORDER BY a.billPullDate ASC")
    Date getMBSBillPullDateBrim(@Param("custId") String custId, @Param("systemName") String systemName);

    @Query("SELECT a.billPullDate FROM MBSBillPullDetail a, CustomerDetail b WHERE trim(b.custBillCycle) = TO_CHAR(a.billPullDate,'DD') AND TO_CHAR(add_months(SYSDATE, 1), 'MM') = TO_CHAR(a.billPullDate,'MM') AND TO_CHAR(SYSDATE, 'YYYY') = TO_CHAR(a.billPullDate,'YYYY') AND a.billRunIndr = 'N' AND TRIM(b.custId) = TRIM(:custId) AND TRIM(b.systemName)= TRIM(:systemName) AND TRIM(a.systemName)= TRIM(:systemName) ORDER BY a.billPullDate ASC")
    Date getMBSBillPullDateNextMonthBrim(@Param("custId") String custId, @Param("systemName") String systemName);

    /*** Batch Queries ***/

    @Query(value = "SELECT * FROM BILLPULL_DETAIL WHERE TRUNC(BILL_RUN_DATE) <= TRUNC(SYSDATE) AND BILL_RUN_INDR = 'N' AND BILL_REL_INDR = 'Y' AND TRIM(SYSTEM_NAME) = :systemName", nativeQuery = true)
    List<MBSBillPullDetail> findCurrentBillPullDates(@Param("systemName") String systemName);

    @Query(value = "SELECT * FROM BILLPULL_DETAIL WHERE TRUNC(BILL_RUN_DATE) <= TRUNC(SYSDATE) AND BILL_RUN_INDR = 'Y' AND BILL_REL_INDR = 'Y' AND BILL_RERUN_INDR = 'N' AND BILL_MEDIA_INDR <> 'P' AND TRIM(SYSTEM_NAME) = :systemName ORDER BY BILL_RUN_DATE DESC", nativeQuery = true)
    List<MBSBillPullDetail> findLatestEligibleBillPullForFormatting(@Param("systemName") String systemName);

    @Query("SELECT a FROM MBSBillPullDetail a WHERE TRUNC(a.billPullDate) = :billPullDate AND TRIM(a.systemName) = TRIM(:systemName)")
    List<MBSBillPullDetail> findByBillPullDateAndSystemName(Date billPullDate, String systemName);

    @Query("SELECT a FROM MBSBillPullDetail a WHERE TRIM(a.systemName) = TRIM(:systemName) AND a.billReRunIndr = 'Y' AND a.billRelIndr = 'Y' AND a.billRunIndr = 'Y' AND a.billMediaIndr <> 'P' ORDER BY a.billPullDate ASC")
    List<MBSBillPullDetail> findBillReRunIndicator(@Param("systemName") String systemName);

    @Transactional
    @Modifying
    @Query("UPDATE MBSBillPullDetail a SET a.billRunIndr = 'Y' where TRUNC(a.billPullDate) = TRUNC(:billPullDate) AND TRIM(a.systemName)= TRIM(:systemName)")
    int UpdateBillRunIndr(@Param("billPullDate") Date billPullDate, @Param("systemName") String systemName);
}
```

---

## 5. Controller (MBS API — BatchInvoiceController rerun endpoints)

### File: `mbs-app-API/src/main/java/com/lumen/mbs/controller/BatchInvoiceController.java` (rerun endpoints only)

```java
package com.lumen.mbs.controller;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.lumen.mbs.batch.dto.BillRerunResult;
import com.lumen.mbs.batch.common.utils.MiscUtils;
import com.lumen.mbs.batch.service.BatchBillRerunService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/batch-invoice")
@Tag(name = "Batch Invoice Controller", description = "APIs for processing batch invoice operations")
public class BatchInvoiceController {

    private static final Logger logger = LoggerFactory.getLogger(BatchInvoiceController.class);

    @Autowired
    private BatchBillRerunService batchBillRerunService;

    // ... other service autowires and endpoints ...

    /**
     * RERUN - Triggers the rerun billing process for a single customer
     * Example: POST /batch-invoice/rerun-billing/customer?customerId=MNCZUC20&billDate=22-AUG-2025&systemName=LEXCIS
     */
    @Operation(summary = "Rerun billing for a single customer")
    @PostMapping("/rerun-billing/customer")
    public ResponseEntity<String> rerunBillingForCustomer(
            @Parameter(description = "Customer ID", required = true) @RequestParam String customerId,
            @Parameter(description = "Bill date in dd-MMM-yy or dd-MMM-yyyy format", required = true) @RequestParam String billDate,
            @Parameter(description = "System name", required = true) @RequestParam String systemName) {

        Date parsedDate;
        try {
            parsedDate = MiscUtils.convertStringToTruncatedDate(billDate);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body("Invalid date format for billPullDate. Expected dd-MMM-yy (e.g., 22-MAY-25 / 22-MAY-2025).");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy");
        String formattedDate = sdf.format(parsedDate);

        // Check rerun eligibility before triggering rerun
        if (!batchBillRerunService.isBillReRunRequired(systemName, parsedDate)) {
            return ResponseEntity.ok("No eligible bill cycle found for customer " + customerId
                    + " for system " + systemName + " and bill date " + formattedDate + ".");
        }

        boolean success = batchBillRerunService.runBillReRunForCustomer(customerId, parsedDate, systemName);

        if (success) {
            return ResponseEntity.ok("Billing cycle rerun completed successfully for customer " + customerId
                    + " for bill cycle: " + formattedDate + " in system: " + systemName);
        } else {
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .body("Billing cycle rerun completed with errors for customer " + customerId
                            + " for bill cycle: " + formattedDate + " in system: " + systemName + ". Check logs for details.");
        }
    }

    /**
     * RERUN - Triggers the rerun billing process for all eligible bill cycles in a system
     * Example: POST /batch-invoice/rerun-billing/system?systemName=LEXCIS
     */
    @Operation(summary = "Rerun billing for all eligible bill cycles in a system")
    @PostMapping("/rerun-billing/system")
    public ResponseEntity<String> rerunBillingForSystem(@RequestParam String systemName) {

        if (systemName == null || systemName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("System name is required and cannot be empty.");
        }

        try {
            BillRerunResult result = batchBillRerunService.runBillReRunForSystem(systemName);

            List<Date> successfulCycles = Optional.ofNullable(result.getSuccessfulCycles()).orElse(Collections.emptyList());
            List<Date> failedCycles = Optional.ofNullable(result.getFailedCycles()).orElse(Collections.emptyList());
            int totalCycles = successfulCycles.size() + failedCycles.size();

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy");
            String successDates = successfulCycles.stream().map(sdf::format).collect(Collectors.joining(", "));
            String failedDates = failedCycles.stream().map(sdf::format).collect(Collectors.joining(", "));

            if (totalCycles == 0) {
                return ResponseEntity.ok(String.format("No eligible bill cycles found for system %s.", systemName));
            }

            boolean allSuccessful = failedCycles.isEmpty() && !result.isHasErrors();
            boolean allFailed = successfulCycles.isEmpty();

            HttpStatus status;
            String message;

            if (allSuccessful) {
                status = HttpStatus.OK;
                message = String.format("Bill rerun for all %d bill cycles completed successfully for system %s: %s.", totalCycles, systemName, successDates);
            } else if (allFailed) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                message = String.format("Bill rerun for all %d bill cycles failed for system %s: %s. Check logs for details.", totalCycles, systemName, failedDates);
            } else {
                status = HttpStatus.PARTIAL_CONTENT;
                message = String.format("Bill rerun partially completed for system %s. Successful: %d cycles (%s). Failed: %d cycles (%s). Check logs for details.",
                        systemName, successfulCycles.size(), successDates, failedCycles.size(), failedDates);
            }

            return ResponseEntity.status(status).body(message);
        } catch (Exception ex) {
            logger.error("System bill rerun failed for system: " + systemName, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Bill rerun failed for system: " + systemName + ". " + ex.getMessage());
        }
    }
}
```

---

## 6. Rerun Constants

### File: `mbs-app-API/src/main/java/com/lumen/mbs/batch/common/constant/MbsBatchConstants.java` (rerun-related section)

```java
/*************************Rerun Constants****************/
public static final String INV_STATUS_PAID = "PAID";
public static final String BILL_COMPLETE_YES = "Y";
public static final String BILL_COMPLETE_NO = "N";
public static final String INDICATOR_NO = "N";
public static final String INDICATOR_YES = "Y";
public static final String CUST_STATUS_INACTIVE = "INACTIVE";
```

---

## 7. MBS GUI — BillControlController and BillCompare.jsp (rerun trigger screen)

**Note:** These are on the `master` branch of the GUI project (not affected by the API branch switch). Included for completeness as they expose the rerun trigger to the billing agent via the GUI.

### File: `mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/controller/BNCController.java` (rerun methods)

```java
@GetMapping(path = "/getAvailableSystemsForBillReRun", produces = MediaType.APPLICATION_JSON_VALUE)
public @ResponseBody List<Map<String, String>> getAvailableSystemsForBillReRun(
        @RequestParam("billPullDate") String aBillPullDate) {
    try {
        Date mBillPullDate = TimestampUtil.getDateFromISO8601String(aBillPullDate);
        List<String> systems = billPullService.getAvailableSystemsForBillReRun(mBillPullDate);
        List<Map<String, String>> result = systems.stream().map(systemName -> {
            Map<String, String> map = new HashMap<>();
            map.put("systemName", systemName);
            return map;
        }).collect(Collectors.toList());
        return result;
    } catch (Exception e) {
        logger.error("Exception in getAvailableSystemsForBillReRun:", e);
        return new ArrayList<>();
    }
}

@PostMapping(path = "/updateBillReRunIndrToY", produces = MediaType.APPLICATION_JSON_VALUE)
public @ResponseBody int updateBillReRunIndrToY(@RequestParam("billPullDate") String aBillPullDate,
        @RequestBody List<String> systemNames) {
    int rowUpdated = 0;
    try {
        Date mBillPullDate = TimestampUtil.getDateFromISO8601String(aBillPullDate);
        rowUpdated = billPullService.updateBillReRunIndrToYForMultipleSystems(mBillPullDate, systemNames);
    } catch (Exception e) {
        logger.error("Exception in updateBillReRunIndrToY:", e);
    }
    return rowUpdated;
}
```

### File: `mbs-app-GUI/mbsgui-master/src/main/webapp/WEB-INF/jsp/BillCompare.jsp` (rerun UI snippet)

```html
<div style="display: none;" id="bill_rerun_btn" align="right">
    <input type="button" value="Bill ReRun" id="bill_rerun" onclick="billReRunProcess();" style="font-weight: bold">
</div>
```

The `billReRunProcess()` JavaScript function calls the `/updateBillReRunIndrToY` endpoint. The button is displayed when `billRelIndr = 'Y'` AND `billRunIndr = 'Y'` AND `billMediaIndr = 'N'` (per the `findBillReRunIndicator` repository query).

### File: `mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/repository/MBSBillPullDetailRepository.java` (GUI-side rerun queries)

```java
@Query(
    "SELECT DISTINCT d.systemName " +
    "FROM MBSBillPullDetail d " +
    "WHERE d.systemName IN ( " +
    "  SELECT DISTINCT c.systemName " +
    "  FROM BillControl c " +
    "  WHERE c.billPullDate = :billPullDate " +
    ") " +
    "AND d.billRelIndr = 'Y' " +
    "AND d.billRunIndr  = 'Y' " +
    "AND d.billMediaIndr = 'N' " +
    "AND d.billPullDate = :billPullDate"
)
List<String> getAvailableSystemsForBillReRunAndReleaseMedia(@Param("billPullDate") Date billPullDate);

@Transactional
@Modifying
@Query("UPDATE MBSBillPullDetail a SET billReRunIndr = 'Y' WHERE a.billPullDate = :billPullDate AND TRIM(a.systemName) IN :systemNames")
int updateBillReRunIndrToYForMultipleSystems(@Param("billPullDate") Date billPullDate, @Param("systemNames") List<String> systemNames);
```

---

*End of extraction.*

