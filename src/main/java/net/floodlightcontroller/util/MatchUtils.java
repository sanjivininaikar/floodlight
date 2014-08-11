package net.floodlightcontroller.util;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.ArpOpcode;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.ICMPv4Code;
import org.projectfloodlight.openflow.types.ICMPv4Type;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpDscp;
import org.projectfloodlight.openflow.types.IpEcn;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U8;
import org.projectfloodlight.openflow.types.VlanPcp;

/**
 * Match helper functions. Use with any OpenFlowJ-Loxi Match.
 * 
 * Includes string methods adopted from OpenFlowJ for OpenFlow 1.0.
 *
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 * @author David Erickson (daviderickson@cs.stanford.edu)
 * @author Rob Sherwood (rob.sherwood@stanford.edu)
 */
public class MatchUtils {
	/* List of Strings for marshalling and unmarshalling to human readable forms.
	 * Classes that convert from Match and String should reference these fields for a
	 * common string representation throughout the controller. The StaticFlowEntryPusher
	 * is one such example that references these strings.
	 */
	public static final String STR_IN_PORT = "ingress_port";

	public static final String STR_DL_DST = "dl_dst";
	public static final String STR_DL_SRC = "dl_src";
	public static final String STR_DL_TYPE = "dl_type";
	public static final String STR_DL_VLAN = "dl_vlan";
	public static final String STR_DL_VLAN_PCP = "dl_vpcp";

	public static final String STR_NW_DST = "nw_dst";
	public static final String STR_NW_SRC = "nw_src";
	public static final String STR_NW_PROTO = "nw_proto";
	public static final String STR_NW_TOS = "nw_tos";

	public static final String STR_TP_DST = "tp_dst";
	public static final String STR_TP_SRC = "tp_src";

	public static final String STR_ICMP_TYPE = "icmp_type";
	public static final String STR_ICMP_CODE = "icmp_code";

	public static final String STR_ARP_OPCODE = "arp_opcode";
	public static final String STR_ARP_SHA = "arp_sha";
	public static final String STR_ARP_DHA = "arp_dha";
	public static final String STR_ARP_SPA = "arp_spa";
	public static final String STR_ARP_DPA = "arp_dpa";

	public static final String STR_MPLS_LABEL = "mpls_label";
	public static final String STR_MPLS_TC = "mpls_tc";
	public static final String STR_MPLS_BOS = "mpls_bos";

	public static final String STR_METADATA = "metadata";
	public static final String STR_TUNNEL_ID = "tunnel_id";

	public static final String STR_PBB_ISID = "pbb_isid";	

	/**
	 * Create a point-to-point match for two devices at the IP layer.
	 * Takes an existing match (e.g. from a PACKET_IN), and masks all
	 * MatchFields leaving behind:
	 * 		IN_PORT
	 * 		VLAN_VID
	 * 		ETH_TYPE
	 * 		ETH_SRC
	 * 		ETH_DST
	 * 		IPV4_SRC
	 * 		IPV4_DST
	 * 		IP_PROTO (might remove this)
	 * 
	 * If one of the above MatchFields is wildcarded in Match m,
	 * that MatchField will be wildcarded in the returned Match.
	 * 
	 * @param m The match to remove all L4+ MatchFields from
	 * @return A new Match object with all MatchFields masked/wildcared
	 * except for those listed above.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Match maskL4AndUp(Match m) {
		Match.Builder mb = m.createBuilder(); 
		Iterator<MatchField<?>> itr = m.getMatchFields().iterator(); // only get exact or masked fields (not fully wildcarded)
		while(itr.hasNext()) {
			MatchField mf = itr.next();
			// restrict MatchFields only to L3 and below: IN_PORT, ETH_TYPE, ETH_SRC, ETH_DST, IPV4_SRC, IPV4_DST, IP_PROTO (this one debatable...)
			// if a MatchField is not in the access list below, it will not be set --> it will be left wildcarded (default)
			if (mf.equals(MatchField.IN_PORT) || mf.equals(MatchField.ETH_TYPE) || mf.equals(MatchField.ETH_SRC) || mf.equals(MatchField.ETH_DST) ||
					mf.equals(MatchField.IPV4_SRC) || mf.equals(MatchField.IPV4_DST) || mf.equals(MatchField.IP_PROTO)) {
				if (m.isExact(mf)) {
					mb.setExact(mf, m.get(mf));
				} else if (m.isPartiallyMasked(mf)) {
					mb.setMasked(mf, m.getMasked(mf));
				} else {
					// it's either exact, masked, or wildcarded
					// itr only contains exact and masked MatchFields
					// we should never get here
				}
			}
		}
		return mb.build();
	}

	/**
	 * Create a builder from an existing Match object. Unlike Match's
	 * createBuilder(), this utility function will preserve all of
	 * Match m's MatchFields, even if new MatchFields are set or modified
	 * with the builder after it is returned to the calling function.
	 * 
	 * All original MatchFields in m will be set if the build() method is 
	 * invoked upon the returned builder. After the builder is returned, if
	 * a MatchField is modified via setExact(), setMasked(), or wildcard(),
	 * the newly modified MatchField will replace the original found in m.
	 * 
	 * @param m; the match to create the builder from
	 * @return Match.Builder; the builder that can be modified, and when built,
	 * will retain all of m's MatchFields, unless you explicitly overwrite them.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Match.Builder createRetentiveBuilder(Match m) {
		/* Builder retains a parent MatchField list, but list will not be used to  
		 * build the new match if the builder's set methods have been invoked; only 
		 * additions will be built, and all parent MatchFields will be ignored,  
		 * even if they were not modified by the new builder. Create a builder, and
		 * walk through m's list of non-wildcarded MatchFields. Set them all in the
		 * new builder by invoking a set method for each. This will make them persist
		 * in the Match built from this builder if the user decides to add or subtract
		 * from the MatchField list.
		 */
		Match.Builder mb = m.createBuilder(); 
		Iterator<MatchField<?>> itr = m.getMatchFields().iterator(); // only get exact or masked fields (not fully wildcarded)
		while(itr.hasNext()) {
			MatchField mf = itr.next();
			if (m.isExact(mf)) {
				mb.setExact(mf, m.get(mf));
			} else if (m.isPartiallyMasked(mf)) {
				mb.setMasked(mf, m.getMasked(mf));
			} else {
				// it's either exact, masked, or wildcarded
				// itr only contains exact and masked MatchFields
				// we should never get here
			}
		}
		return mb;
	}

	/**
	 * Create a Match builder the same OF version as Match m. The returned builder
	 * will not retain any MatchField information from Match m and will
	 * essentially return a clean-slate Match builder with no parent history. 
	 * This simple method is included as a wrapper to provide the opposite functionality
	 * of createRetentiveBuilder().
	 * 
	 * @param m; the match to create the builder from
	 * @return Match.Builder; the builder retains no history from the parent Match m
	 */
	public static Match.Builder createForgetfulBuilder(Match m) {
		return OFFactories.getFactory(m.getVersion()).buildMatch();
	}

	/**
	 * Create a duplicate Match object from Match m.
	 * 
	 * @param m; the match to copy
	 * @return Match; the new copy of Match m
	 */
	public static Match createCopy(Match m) {
		return m.createBuilder().build(); // will use parent MatchFields to produce the new Match only if the builder is never modified
	}

	/**
	 * TODO @Ryan NOT IMPLEMENTED! Returns empty string right now.
	 * Output a dpctl-styled string, i.e., only list the elements that are not wildcarded.
	 * 
	 * A match-everything Match outputs "Match[]"
	 * 
	 * @return "Match[dl_src:00:20:01:11:22:33,nw_src:192.168.0.0/24,tp_dst:80]"
	 */
	public static String toString(Match match) {
		/*String str = "";

	        match

	        // l1
	        if ((wildcards & OFPFW_IN_PORT) == 0)
	            str += "," + STR_IN_PORT + "=" + U16.f(this.inputPort);

	        // l2
	        if ((wildcards & OFPFW_DL_DST) == 0)
	            str += "," + STR_DL_DST + "="
	                    + match.);
	        if ((wildcards & OFPFW_DL_SRC) == 0)
	            str += "," + STR_DL_SRC + "="
	                    + HexString.toHexString(this.dataLayerSource);
	        if ((wildcards & OFPFW_DL_TYPE) == 0)
	            str += "," + STR_DL_TYPE + "=0x"
	                    + Integer.toHexString(U16.f(this.dataLayerType));
	        if ((wildcards & OFPFW_DL_VLAN) == 0)
	            str += "," + STR_DL_VLAN + "=0x"
	                    + Integer.toHexString(U16.f(this.dataLayerVirtualLan));
	        if ((wildcards & OFPFW_DL_VLAN_PCP) == 0)
	            str += ","
	                    + STR_DL_VLAN_PCP
	                    + "="
	                    + Integer.toHexString(U8
	                            .f(this.dataLayerVirtualLanPriorityCodePoint));

	        // l3
	        if (getNetworkDestinationMaskLen() > 0)
	            str += ","
	                    + STR_NW_DST
	                    + "="
	                    + cidrToString(networkDestination,
	                            getNetworkDestinationMaskLen());
	        if (getNetworkSourceMaskLen() > 0)
	            str += "," + STR_NW_SRC + "="
	                    + cidrToString(networkSource, getNetworkSourceMaskLen());
	        if ((wildcards & OFPFW_NW_PROTO) == 0)
	            str += "," + STR_NW_PROTO + "=" + U8.f(this.networkProtocol);
	        if ((wildcards & OFPFW_NW_TOS) == 0)
	            str += "," + STR_NW_TOS + "=" + U8.f(this.networkTypeOfService);

	        // l4
	        if ((wildcards & OFPFW_TP_DST) == 0)
	            str += "," + STR_TP_DST + "=" + U16.f(this.transportDestination);
	        if ((wildcards & OFPFW_TP_SRC) == 0)
	            str += "," + STR_TP_SRC + "=" + U16.f(this.transportSource);
	        if ((str.length() > 0) && (str.charAt(0) == ','))
	            str = str.substring(1); // trim the leading ","
	        // done
	        return "OFMatch[" + str + "]"; */
		return "";
	}

	/**
	 * Based on the method from OFMatch in openflowj 1.0.
	 * Set this Match's parameters based on a comma-separated key=value pair
	 * dpctl-style string, e.g., from the output of OFMatch.toString() <br>
	 * <p>
	 * Supported keys/values include <br>
	 * <p>
	 * <TABLE border=1>
	 * <TR>
	 * <TD>KEY(s)
	 * <TD>VALUE
	 * </TR>
	 * <TR>
	 * <TD>"in_port","input_port"
	 * <TD>integer
	 * </TR>
	 * <TR>
	 * <TD>"dl_src", "dl_dst"
	 * <TD>hex-string
	 * </TR>
	 * <TR>
	 * <TD>"dl_type", "dl_vlan", "dl_vlan_pcp"
	 * <TD>integer
	 * </TR>
	 * <TR>
	 * <TD>"nw_src", "nw_dst"
	 * <TD>CIDR-style netmask
	 * </TR>
	 * <TR>
	 * <TD>"tp_src","tp_dst"
	 * <TD>integer (max 64k)
	 * </TR>
	 * </TABLE>
	 * <p>
	 * The CIDR-style netmasks assume 32 netmask if none given, so:
	 * "128.8.128.118/32" is the same as "128.8.128.118"
	 * 
	 * @param match
	 *            a key=value comma separated string, e.g.
	 *            "in_port=5,nw_dst=192.168.0.0/16,tp_src=80"
	 * @throws IllegalArgumentException
	 *             on unexpected key or value
	 */
	public static Match fromString(String match, OFVersion ofVersion) throws IllegalArgumentException {
		if (match.equals("") || match.equalsIgnoreCase("any") || match.equalsIgnoreCase("all") || match.equals("[]")) {
			match = "Match[]";
		}
		
		// Split into pairs of key=value
		String[] tokens = match.split("[\\[,\\]]");
		int initArg = 0;
		if (tokens[0].equals("Match")) {
			initArg = 1;
		}
		
		// Split up key=value pairs into [key, value], and insert into linked list
		int i;
		String[] tmp;
		ArrayDeque<String[]> llValues = new ArrayDeque<String[]>();
		for (i = initArg; i < tokens.length; i++) {
			tmp = tokens[i].split("=");
			if (tmp.length != 2) {
				throw new IllegalArgumentException("Token " + tokens[i] + " does not have form 'key=value' parsing " + match);
			}
			tmp[0] = tmp[0].toLowerCase(); // try to make key parsing case insensitive
			llValues.add(tmp); // llValues contains [key, value] pairs. Create a queue of pairs to process.
		}	

		Match.Builder mb = OFFactories.getFactory(ofVersion).buildMatch();

		while (!llValues.isEmpty()) {
			IpProtocol ipProto; // used to prevent lots of match.get()'s for detecting transport protocol
			String[] key_value = llValues.pollFirst(); // pop off the first element; this completely removes it from the queue.
			switch (key_value[0]) {
			case STR_IN_PORT:
				mb.setExact(MatchField.IN_PORT, OFPort.of(Integer.valueOf(key_value[1])));
				break;
			case STR_DL_DST:
				mb.setExact(MatchField.ETH_DST, MacAddress.of(key_value[1]));
				break;
			case STR_DL_SRC:
				mb.setExact(MatchField.ETH_SRC, MacAddress.of(key_value[1]));
				break;
			case STR_DL_TYPE:
				if (key_value[1].startsWith("0x")) {
					mb.setExact(MatchField.ETH_TYPE, EthType.of(Integer.valueOf(key_value[1].replaceFirst("0x", ""), 16)));
				} else {
					mb.setExact(MatchField.ETH_TYPE, EthType.of(Integer.valueOf(key_value[1])));
				}
				break;
			case STR_DL_VLAN:
				if (key_value[1].contains("0x")) {
					mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(Integer.valueOf(key_value[1].replaceFirst("0x", ""), 16)));
				} else {
					mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(Integer.valueOf(key_value[1])));
				}
				break;
			case STR_DL_VLAN_PCP:
				mb.setExact(MatchField.VLAN_PCP, VlanPcp.of(U8.t(Short.valueOf(key_value[1]))));
				break;
			case STR_NW_DST:
				mb.setMasked(MatchField.IPV4_DST, IPv4AddressWithMask.of(key_value[1]));
				break;
			case STR_NW_SRC:
				mb.setMasked(MatchField.IPV4_SRC, IPv4AddressWithMask.of(key_value[1]));
				break;
			case STR_NW_PROTO:
				mb.setExact(MatchField.IP_PROTO, IpProtocol.of(Short.valueOf(key_value[1])));
				break;
			case STR_NW_TOS:
				mb.setExact(MatchField.IP_ECN, IpEcn.of(U8.t(Short.valueOf(key_value[1]))));
				mb.setExact(MatchField.IP_DSCP, IpDscp.of(U8.t(Short.valueOf(key_value[1]))));
				break;
			case STR_TP_DST:
				// if we don't know the transport protocol yet, postpone parsing this [key, value] pair until we know. Put it at the back of the queue.
				if ((ipProto = mb.get(MatchField.IP_PROTO)) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else if (ipProto.equals(IpProtocol.TCP)) {
					mb.setExact(MatchField.TCP_DST, TransportPort.of(Integer.valueOf(key_value[1])));
				} else if (ipProto.equals(IpProtocol.UDP)) {
					mb.setExact(MatchField.UDP_DST, TransportPort.of(Integer.valueOf(key_value[1])));
				} else if (ipProto.equals(IpProtocol.SCTP)) {
					mb.setExact(MatchField.SCTP_DST, TransportPort.of(Integer.valueOf(key_value[1])));
				}
				break;
			case STR_TP_SRC:
				if ((ipProto = mb.get(MatchField.IP_PROTO)) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.TCP)) {
					mb.setExact(MatchField.TCP_SRC, TransportPort.of(Integer.valueOf(key_value[1])));
				} else if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.UDP)) {
					mb.setExact(MatchField.UDP_SRC, TransportPort.of(Integer.valueOf(key_value[1])));
				} else if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.SCTP)) {
					mb.setExact(MatchField.SCTP_SRC, TransportPort.of(Integer.valueOf(key_value[1])));
				}
				break;
			case STR_ICMP_TYPE:
				mb.setExact(MatchField.ICMPV4_TYPE, ICMPv4Type.of(Short.parseShort(key_value[1])));
				break;
			case STR_ICMP_CODE:
				mb.setExact(MatchField.ICMPV4_CODE, ICMPv4Code.of(Short.parseShort(key_value[1])));
				break;
			case STR_ARP_OPCODE:
				mb.setExact(MatchField.ARP_OP, ArpOpcode.of(Integer.parseInt(key_value[1])));
				break;
			case STR_ARP_SHA:
				mb.setExact(MatchField.ARP_SHA, MacAddress.of(key_value[1]));
				break;
			case STR_ARP_DHA:
				mb.setExact(MatchField.ARP_THA, MacAddress.of(key_value[1]));
				break;
			case STR_ARP_SPA:
				mb.setExact(MatchField.ARP_SPA, IPv4Address.of(key_value[1]));
				break;
			case STR_ARP_DPA:
				mb.setExact(MatchField.ARP_TPA, IPv4Address.of(key_value[1]));
				break;
			case STR_MPLS_LABEL:
				mb.setExact(MatchField.MPLS_LABEL, U32.of(Long.parseLong(key_value[1])));
				break;
			case STR_MPLS_TC:
				mb.setExact(MatchField.MPLS_TC, U8.of(Short.parseShort(key_value[1])));
				break;
			case STR_MPLS_BOS:
				//no-op. Not implemented.
				break;
			case STR_METADATA:
				mb.setExact(MatchField.METADATA, OFMetadata.ofRaw(Long.parseLong(key_value[1])));
				break;
			case STR_TUNNEL_ID:
				//no-op. Not implemented.
				break;
			case STR_PBB_ISID:
				//no-op. Not implemented.
				break;
			default:
				throw new IllegalArgumentException("unknown token " + key_value + " parsing " + match);
			} 
		}
		return mb.build();
	}
}