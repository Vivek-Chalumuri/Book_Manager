"""
ref: https://dialogflow.com/docs/fulfillment/how-it-works
"""
import os
import sys
import flask
import json
import csv
import pandas as pd
from flask import Flask, request


app = Flask(__name__)
dirname = os.path.dirname(__file__)

@app.route('/',  methods=['POST'])
def webhook_entry():
    """ the main function to handle incoming webhook request
    """
    res_dict = dict()

    # parse content
    content = request.json

    # with open(os.path.join(dirname, "income_request.json"), "w") as f:
    #     json.dump(content, f, indent=2)

    intent = content['queryResult']['intent']['displayName']

    book_info = dict()

    if intent == "Intent.GetBookInformation":
        RFID = content['queryResult']['parameters']['RFID']
        book_info = get_book_info(RFID)
        if book_info != None:
            res_dict['fulfillmentText'] = (
                "The book is owned by {owner} and in {location}".format(**book_info))

        else:
            res_dict['fulfillmentText'] = "Unable to find a book with this RFID!"
            res_dict['followupEventInput'] = {
                "name": "ask_if_creat_new_book",
                "parameters": {
                    "RFID": RFID
                }
            }
    elif intent == "Intent.create_new_book - yes":
        output_contexts = content['queryResult']['outputContexts']

        book_info = dict()
        out_context = get_RFID_content(output_contexts)

        book_info['owner'] = out_context['parameters']['Owner']
        book_info['RFID'] = out_context['parameters']['RFID']
        book_info['location'] = out_context['parameters']['Location']

        # write record
        write_book_info_to_db(book_info)
        res_dict['fulfillmentText'] = (
            "Successfully wrote the info to database! "
            "The book is owned by {owner} and in {location}.".format(**book_info)
        )

    elif intent == "Intent.remove_book_record - yes":
        output_contexts = content['queryResult']['outputContexts']
        out_context = get_RFID_content(output_contexts)
        RFID = out_context['parameters']['RFID']
        remove_book_info_from_db(RFID)
        res_dict['fulfillmentText'] = "Removed the specified book."

    elif intent == "Intent.update_owner - yes":
        output_contexts = content['queryResult']['outputContexts']
        out_context = get_RFID_content(output_contexts)
        book_info['RFID'] = out_context['parameters']['RFID']
        book_info['owner'] = out_context['parameters']['UpdatedOwner']
        update_owner(book_info)
        res_dict['fulfillmentText'] = "Updated the book owner to %s." % book_info['owner']

    elif intent == "Intent.update_location - yes":
        output_contexts = content['queryResult']['outputContexts']
        out_context = get_RFID_content(output_contexts)
        book_info['RFID'] = out_context['parameters']['RFID']
        book_info['location'] = out_context['parameters']['UpdatedLocation']
        update_location(book_info)
        res_dict['fulfillmentText'] = "Updated the book location to %s." % book_info['location']


    # jsonize the response
    ret = flask.jsonify(res_dict)
    return ret


def get_RFID_content(output_contexts):
    for out_context in output_contexts:
        if out_context['name'].endswith("contexts/rfid"):
            return out_context


def get_book_info(RFID):
    try:
        df = pd.read_csv(os.path.join(dirname, "book_info.csv"))
    except IOError as e:
        print(e, file=sys.stderr)
        return None
    q_result = df[df.RFID == RFID]
    if q_result.empty:
        return None
    else:
        return q_result.iloc[0].to_dict()

def write_book_info_to_db(info):
    csv_file = os.path.join(dirname, "book_info.csv")
    df = pd.read_csv(csv_file)
    df = df.append(info, ignore_index=True)
    df.to_csv(csv_file, index=False, quoting=csv.QUOTE_ALL)

def remove_book_info_from_db(RFID):
    csv_file = os.path.join(dirname, "book_info.csv")
    df = pd.read_csv(csv_file)
    df = df.drop(df[df.RFID == RFID].index)
    df.to_csv(csv_file, index=False, quoting=csv.QUOTE_ALL)

def update_owner(info):
    csv_file = os.path.join(dirname, "book_info.csv")
    df = pd.read_csv(csv_file)

    # update dataframe
    df.loc[df.RFID == info['RFID'], "owner"] = info['owner']

    df.to_csv(csv_file, index=False, quoting=csv.QUOTE_ALL)

def update_location(info):
    csv_file = os.path.join(dirname, "book_info.csv")
    df = pd.read_csv(csv_file)

    # update dataframe
    df.loc[df.RFID == info['RFID'], "location"] = info['location']

    df.to_csv(csv_file, index=False, quoting=csv.QUOTE_ALL)

if __name__ == '__main__':
    app.run(port=5000, debug=True)
