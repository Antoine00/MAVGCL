/****************************************************************************
 *
 *   Copyright (c) 2016 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.flight.log.ulog;

import java.util.Map;
import java.util.concurrent.locks.LockSupport;

import org.mavlink.messages.MAV_CMD;
import org.mavlink.messages.MAV_SEVERITY;
import org.mavlink.messages.lquac.msg_logging_ack;
import org.mavlink.messages.lquac.msg_logging_data;
import org.mavlink.messages.lquac.msg_logging_data_acked;
import org.mavlink.messages.lquac.msg_serial_control;

import com.comino.flight.prefs.MAVPreferences;
import com.comino.jmavlib.extensions.UlogMAVLinkParser;
import com.comino.mav.control.IMAVController;
import com.comino.msp.log.MSPLogger;
import com.comino.msp.main.control.listener.IMAVLinkListener;


public class ULogFromMAVLinkReader implements IMAVLinkListener {

	private final int STATE_HEADER_IDLE				= 0;
	private final int STATE_HEADER_WAIT				= 1;
	private final int STATE_DATA            		= 2;

	private IMAVController control   = null;
	private int state = STATE_HEADER_IDLE;
	private UlogMAVLinkParser parser = null;
	private int package_processed = 0;


	public ULogFromMAVLinkReader(IMAVController control)  {
		this.parser = new UlogMAVLinkParser();
		this.control = control;
		this.control.addMAVLinkListener(this);
	}

	public Map<String, Object> getData() {
		return parser.getDataBuffer();
	}

	public Map<String,String> getFieldList() {
		return parser.getFieldList();
	}

	public void enableLogging(boolean enable) {

		state=STATE_HEADER_IDLE;

		if(!MAVPreferences.getInstance().getBoolean(MAVPreferences.ULOGGER, false)) {
			return;
		}

		long tms = System.currentTimeMillis();

		if(enable)  {
			System.out.println("Start ulogging...");
			control.sendMAVLinkCmd(MAV_CMD.MAV_CMD_LOGGING_START,0);
			while(state!=STATE_DATA ) {
				LockSupport.parkNanos(10000000);
				if((System.currentTimeMillis()-tms)>500) {
					MSPLogger.getInstance().writeLocalMsg("Logging via MAVLink streaming",MAV_SEVERITY.MAV_SEVERITY_NOTICE);
					control.sendMAVLinkCmd(MAV_CMD.MAV_CMD_LOGGING_STOP);
					return;
				}
			}
			MSPLogger.getInstance().writeLocalMsg("Logging via ULog streaming",MAV_SEVERITY.MAV_SEVERITY_NOTICE);
		} else {
			control.sendMAVLinkCmd(MAV_CMD.MAV_CMD_LOGGING_STOP);
		}
	}

	public boolean isLogging() {
		return state==STATE_DATA;
	}

	@Override
	public synchronized void received(Object o) {

		if( o instanceof msg_serial_control) {
			msg_logging_ack ack = new msg_logging_ack(255,1);
			ack.target_component=1;
			ack.target_system=1;
			ack.sequence =0;
			control.sendMAVLinkMessage(ack);
		}

		if( o instanceof msg_logging_data_acked) {
			if(state==STATE_HEADER_IDLE || state==STATE_DATA) {
				parser.reset();
				package_processed = 0;
			}
			msg_logging_data_acked log = (msg_logging_data_acked)o;
			msg_logging_ack ack = new msg_logging_ack(255,1);
			ack.target_component=1;
			ack.target_system=1;
			ack.sequence = log.sequence;
			control.sendMAVLinkMessage(ack);
			parser.addToBuffer(log.data, log.length,log.first_message_offset, package_processed == log.sequence);
			if(package_processed != log.sequence) {
				System.err.println(package_processed+":"+log.sequence);
			}

			if(state==STATE_HEADER_IDLE || state==STATE_DATA) {
				if(parser.checkHeader()) {
					state = STATE_HEADER_WAIT;
					System.out.println("Start reading header");
				} else
					return;
			}
			parser.parseHeader();
			package_processed++;
		}

		if( o instanceof msg_logging_data) {

			if(state==STATE_HEADER_IDLE) {
				control.sendMAVLinkCmd(MAV_CMD.MAV_CMD_LOGGING_STOP);
				return;
			}

			if(state==STATE_HEADER_WAIT) {
				parser.buildSubscriptions();
				System.out.println("Header valid: "+parser.getSystemInfo());
				state = STATE_DATA;
			}

			if(state==STATE_DATA) {
				msg_logging_data log = (msg_logging_data)o;
				if(package_processed != log.sequence) {
					System.err.println("ULOG Sequence failed");
					control.sendMAVLinkCmd(MAV_CMD.MAV_CMD_LOGGING_STOP);
					state=STATE_HEADER_IDLE;
				}
				parser.addToBuffer(log.data, log.length,log.first_message_offset, true);
				parser.parseData();
				package_processed++;
			}
		}
	}

	//  helpers for dev
//	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
//	public static String bytesToHex(byte[] bytes, int len) {
//		char[] hexChars = new char[len * 2];
//		for ( int j = 0; j <len; j++ ) {
//			int v = bytes[j] & 0xFF;
//			hexChars[j * 2] = hexArray[v >>> 4];
//			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
//		}
//		return new String(hexChars);
//	}
//
//	public static String intsToHex(int[] bytes, int len) {
//		char[] hexChars = new char[len * 2];
//		for ( int j = 0; j <len; j++ ) {
//			int v = bytes[j] & 0xFF;
//			hexChars[j * 2] = hexArray[v >>> 4];
//			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
//		}
//		return new String(hexChars);
//	}

}
