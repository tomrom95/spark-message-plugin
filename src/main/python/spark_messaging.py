#!/usr/bin/env jython
"""
    Main Python script to execute Spark actions.
    Called from SparkMessaging.java
"""
from Spark import *

def message(room_ids, message, machine_user, machine_pw):
    """ Messages spark room using machine info """
    print "Authenticating using machine account login"
    room_list = room_ids.split(',')
    success = True
    for room in room_list:
        try:
            machine = MachineAccount(machine_user, machine_pw)
            result = machine.message_spark_room(room, message)
            if result is False:
                success = False
        except Exception, ex:
            print "Error messaging room %s for error: %s" %(room, ex)
            success = False
    return success

def add_machine(room_ids, oauth_token, machine_user, machine_pw):
    """ Adds machine account to room using user oauth token """
    print "Adding machine account to rooms"
    room_list = room_ids.split(',')
    success = True
    for room in room_list:
        try:
            user = User(oauth_token)
            machine = MachineAccount(machine_user, machine_pw)
            result = user.add_user_to_spark_room(room, machine)
            if result is False:
                success = False
        except Exception, ex:
            print "Error adding machine account to room %s for error: %s" %(room, ex)
            success = False
    return success

