#!/usr/bin/env jython

#import requests
#from Spark import *

def test(x):
    return True

# def message(room_ids, message, machine_user, machine_pw):

#     print "Authenticating using machine account login"
#     room_list = room_ids.split(',')
#     for room in room_list:
#         try:
#             machine = MachineAccount(machine_user, machine_pw)
#             machine.message_spark_room(room, message)
#         except Exception, ex:
#             print "Error messaging room: %s" %ex
#     return True

# def add_machine(room_id, oauth_token, machine_user, machine_pw):
#     try:
#         user = User(oauth_token)
#         machine = MachineAccount(machine_user, machine_pw)
#         user.add_user_to_spark_room(room_id, machine)
#     except Exception, ex:
#         print "Error adding machine account: %s" %ex
#     return True

